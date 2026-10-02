package com.poppick.poppick.feature.popup

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupViewCountRepository
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupReader
import com.poppick.poppick.feature.popup.implement.PopupViewCountWriter
import com.poppick.poppick.feature.popup.implement.PopupWriter
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 로컬 전용 Postgres 에서 조회수 원자적 증가를 확인한다. **공유 DB 에는 절대 연결하지 않는다.**
 * - POPUP_VIEW_TEST_DB_URL 이 설정된 경우에만 실행되고, localhost · 127.0.0.1 이 아닌 URL 이면 즉시 실패한다.
 * - 스키마는 hbm2ddl=create 로 매번 새로 만든다(대상 DB 의 기존 테이블이 지워진다). pgvector 확장이 필요하다.
 *   예: docker run -d --name poppick-viewcount-pg -e POSTGRES_PASSWORD=test -p 55432:5432 pgvector/pgvector:pg17
 *       docker exec poppick-viewcount-pg psql -U postgres -c "CREATE EXTENSION IF NOT EXISTS vector"
 *       POPUP_VIEW_TEST_DB_URL=jdbc:postgresql://localhost:55432/postgres POPUP_VIEW_TEST_DB_PASSWORD=test
 * - Hibernate 가 만든 view_count 에는 DEFAULT 가 없으므로, 운영 DDL 과 같게 DEFAULT 0 을 붙인 뒤 시작한다.
 */
@EnabledIfEnvironmentVariable(named = "POPUP_VIEW_TEST_DB_URL", matches = ".+")
@SpringBootTest(
    properties = [
        "collection.cron=-",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    ],
)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PopupViewCountIntegrationTest {
    companion object {
        private val LOCAL_HOSTS = listOf("//localhost:", "//127.0.0.1:")

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val url = System.getenv("POPUP_VIEW_TEST_DB_URL")
            check(LOCAL_HOSTS.any { url.contains(it) }) { "로컬 DB(localhost · 127.0.0.1)만 허용한다: $url" }
            registry.add("spring.datasource.hikari.jdbc-url") { url }
            registry.add("spring.datasource.hikari.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.datasource.hikari.username") { System.getenv("POPUP_VIEW_TEST_DB_USERNAME") ?: "postgres" }
            registry.add("spring.datasource.hikari.password") { System.getenv("POPUP_VIEW_TEST_DB_PASSWORD") ?: "" }
        }
    }

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var popupRepository: PopupRepository

    @Autowired
    lateinit var popupViewCountRepository: PopupViewCountRepository

    @Autowired
    lateinit var popupViewCountWriter: PopupViewCountWriter

    @Autowired
    lateinit var popupReader: PopupReader

    @Autowired
    lateinit var popupWriter: PopupWriter

    @BeforeAll
    fun applyProductionDefault() {
        jdbcTemplate.execute("ALTER TABLE popup ALTER COLUMN view_count SET DEFAULT 0")
    }

    private fun newPopupId(): Long = popupWriter.save(Popup(source = SourceType.PERPLEXITY, title = "조회수 테스트 팝업")).id!!

    private fun viewCountInDb(popupId: Long): Long =
        jdbcTemplate.queryForObject("SELECT view_count FROM popup WHERE popup_id = ?", Long::class.java, popupId)!!

    @Test
    fun `JPA INSERT 는 view_count 를 쓰지 않고 DB DEFAULT 0 이 들어간다`() {
        val popupId = newPopupId()

        viewCountInDb(popupId) shouldBe 0L
        popupReader.findById(popupId).viewCount shouldBe 0L
    }

    @Test
    fun `increase 는 1 올리고 올린 뒤 값을 반환한다`() {
        val popupId = newPopupId()

        popupViewCountWriter.increase(popupId) shouldBe 1L
        popupViewCountWriter.increase(popupId) shouldBe 2L
        viewCountInDb(popupId) shouldBe 2L
    }

    @Test
    fun `없는 팝업이면 NULL 이고 다른 행은 바뀌지 않는다`() {
        val popupId = newPopupId()

        popupViewCountWriter.increase(Long.MAX_VALUE) shouldBe null
        viewCountInDb(popupId) shouldBe 0L
    }

    @Test
    fun `동시에 100건을 올려도 유실 없이 100 이 되고 각 호출은 서로 다른 값을 받는다`() {
        val popupId = newPopupId()
        val requests = 100
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(20)
        try {
            val futures = List(requests) { pool.submit<Long?> { start.await().let { popupViewCountWriter.increase(popupId) } } }
            start.countDown()
            val returned = futures.map { it.get(30, TimeUnit.SECONDS) }

            viewCountInDb(popupId) shouldBe requests.toLong()
            returned shouldContainExactlyInAnyOrder (1L..requests).toList()
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `보강 저장(merge)이 예전에 읽은 viewCount 로 최신 조회수를 덮어쓰지 않는다`() {
        val popupId = newPopupId()
        val staleSnapshot = popupReader.findById(popupId)
        repeat(5) { popupViewCountWriter.increase(popupId) }

        // PopupEnricher 와 같은 경로: 예전 스냅샷을 병합해 PopupWriter.save(PopupEntity.from(...))
        popupWriter.save(staleSnapshot.copy(title = "보강된 제목", viewCount = 0))

        viewCountInDb(popupId) shouldBe 5L
        popupReader.findById(popupId).title shouldBe "보강된 제목"
        popupReader.findById(popupId).viewCount shouldBe 5L
    }

    @Test
    fun `스칼라 조회는 DB 의 최신 값을 읽는다`() {
        val popupId = newPopupId()
        popupViewCountWriter.increase(popupId)

        popupViewCountRepository.findViewCount(popupId) shouldBe 1L
        popupRepository.findById(popupId).get().viewCount shouldBe 1L
    }
}
