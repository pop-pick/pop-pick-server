package com.poppick.poppick.feature.popuplist

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter
import com.poppick.poppick.feature.popuplist.implement.PopupListReader
import com.poppick.poppick.global.util.KST
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.LocalDate

/**
 * 로컬 전용 Postgres 에 카테고리 · 지역이 다른 팝업을 만들어 회원 추천 후보 조회(카테고리 OR 지역 · 노출 조건 · 인기순)를 확인한다.
 * **공유 DB 에는 절대 연결하지 않는다.** 실행 조건 · 준비는 PopupViewCountIntegrationTest 와 같다.
 * 다른 테스트 데이터와 섞이지 않도록 실제로 없는 카테고리 · 지역 id(9000 번대)를 쓴다.
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
class PopupRecommendIntegrationTest {
    companion object {
        private val LOCAL_HOSTS = listOf("//localhost:", "//127.0.0.1:")

        private const val CATEGORY = 9001
        private const val TIE_CATEGORY = 9003
        private const val OTHER_CATEGORY = 9002
        private const val AREA = 9101
        private const val OTHER_AREA = 9102

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val url = System.getenv("POPUP_VIEW_TEST_DB_URL")
            check(LOCAL_HOSTS.any { url.contains(it) }) { "로컬 DB(localhost · 127.0.0.1)만 허용한다: $url" }
            registry.add("spring.datasource.hikari.jdbc-url") { url }
            registry.add("spring.datasource.hikari.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.datasource.hikari.username") { System.getenv("POPUP_VIEW_TEST_DB_USERNAME") ?: "postgres" }
            registry.add("spring.datasource.hikari.password") { System.getenv("POPUP_VIEW_TEST_DB_PASSWORD") ?: "" }
            System.getenv("POPUP_VIEW_TEST_REDIS_HOST")?.let { host -> registry.add("spring.data.redis.host") { host } }
        }
    }

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var popupWriter: PopupWriter

    @Autowired
    lateinit var popupListReader: PopupListReader

    private val today = LocalDate.now(KST)
    private val ids = mutableMapOf<String, Long>()

    @BeforeAll
    fun seed() {
        jdbcTemplate.execute("ALTER TABLE popup ALTER COLUMN view_count SET DEFAULT 0")

        fun seed(
            name: String,
            categoryId: Int?,
            areaId: Int?,
            viewCount: Long,
            startDate: LocalDate? = today.minusDays(1),
            endDate: LocalDate? = null,
        ) {
            val popup =
                Popup(
                    source = SourceType.PERPLEXITY,
                    title = "추천 테스트 $name",
                    startDate = startDate,
                    endDate = endDate,
                    interestCategoryId = categoryId,
                    areaId = areaId,
                )
            val id = popupWriter.save(popup).id!!
            jdbcTemplate.update("UPDATE popup SET view_count = ? WHERE popup_id = ?", viewCount, id)
            ids[name] = id
        }

        seed("categoryOnly", CATEGORY, 9998, viewCount = 5)
        seed("areaOnly", 9997, AREA, viewCount = 4)
        seed("both", CATEGORY, AREA, viewCount = 3)
        seed("nullCategoryArea", null, AREA, viewCount = 2)
        seed("nullCategoryNullArea", null, null, viewCount = 9)
        seed("neither", OTHER_CATEGORY, OTHER_AREA, viewCount = 9)
        seed("ended", CATEGORY, AREA, viewCount = 9, endDate = today.minusDays(1))
        seed("notOpened", CATEGORY, AREA, viewCount = 9, startDate = today.plusDays(1))
        seed("openedToday", CATEGORY, null, viewCount = 0, startDate = today)
        seed("tieFirst", TIE_CATEGORY, null, viewCount = 1)
        seed("tieSecond", TIE_CATEGORY, null, viewCount = 1)
    }

    private fun find(
        categoryIds: List<Int>,
        areaIds: List<Int>,
        limit: Int = 50,
    ) = popupListReader.findPreferredPopups(categoryIds, areaIds, today, limit).map { it.id!! }

    private fun idsOf(vararg names: String) = names.map { ids.getValue(it) }

    @Test
    fun `관심 카테고리만 있으면 카테고리가 맞는 노출 팝업만 인기순`() {
        find(listOf(CATEGORY), emptyList()) shouldBe idsOf("categoryOnly", "both", "openedToday")
    }

    @Test
    fun `선호 지역만 있으면 지역이 맞는 노출 팝업만 인기순(카테고리 NULL 이어도 지역이 맞으면 포함)`() {
        find(emptyList(), listOf(AREA)) shouldBe idsOf("areaOnly", "both", "nullCategoryArea")
    }

    @Test
    fun `둘 다 있으면 카테고리 OR 지역이고, 둘 다 맞는 팝업은 한 번만 담긴다`() {
        find(listOf(CATEGORY), listOf(AREA)) shouldBe idsOf("categoryOnly", "areaOnly", "both", "nullCategoryArea", "openedToday")
    }

    @Test
    fun `카테고리 · 지역 모두 맞지 않으면 빈 목록(카테고리 · 지역 NULL 팝업도 담기지 않는다)`() {
        find(listOf(9004), listOf(9104)) shouldBe emptyList()
    }

    @Test
    fun `여러 id 중 하나라도 맞으면 담는다`() {
        find(listOf(OTHER_CATEGORY, 9004), listOf(OTHER_AREA)) shouldBe idsOf("neither")
    }

    @Test
    fun `같은 조회수면 최근 등록순(popup_id DESC)`() {
        find(listOf(TIE_CATEGORY), emptyList()) shouldBe idsOf("tieSecond", "tieFirst")
    }

    @Test
    fun `limit 만큼만 인기순 앞에서 자른다`() {
        find(listOf(CATEGORY), listOf(AREA), limit = 3) shouldBe idsOf("categoryOnly", "areaOnly", "both")
    }

    @Test
    fun `관심 카테고리 · 선호 지역이 모두 비면 조회하지 않고 예외`() {
        // 레포지토리 예외 변환으로 require 의 IllegalArgumentException 이 감싸져 나온다.
        shouldThrow<InvalidDataAccessApiUsageException> { find(emptyList(), emptyList()) }
    }

    @Test
    fun `추천 후보 조회 전후 조회수는 변하지 않는다`() {
        val before = jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id")

        find(listOf(CATEGORY), listOf(AREA))

        jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id") shouldBe before
    }
}
