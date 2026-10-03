package com.poppick.poppick.feature.popup

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.EnrichTargetCriteria
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.util.KST
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 로컬 전용 Postgres 에서 보강 · 이미지 대상 조회 조건을 확인한다. **공유 DB 에는 절대 연결하지 않는다.**
 * 실행 조건 · 준비는 PopupViewCountIntegrationTest 와 같다(POPUP_VIEW_TEST_DB_URL, hbm2ddl=create, pgvector).
 * 테스트마다 popup 테이블을 비우고 시작한다. view_count DEFAULT 0 · FK 대상(interest_category 1 · favorite_area 1)은 직접 맞춘다.
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
class PopupTargetQueryIntegrationTest {
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
            System.getenv("POPUP_VIEW_TEST_REDIS_HOST")?.let { host -> registry.add("spring.data.redis.host") { host } }
        }
    }

    @Autowired
    lateinit var popupRepository: PopupRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val now = OffsetDateTime.now(KST)
    private val today = LocalDate.now(KST)
    private val criteria =
        EnrichTargetCriteria(
            retryLimit = 2,
            retryBefore = now.minusDays(7),
            today = today,
            refreshBefore = now.minusDays(14),
            imminentUntil = today.plusDays(7),
            imminentRefreshBefore = now.minusDays(3),
            limit = 100,
        )

    @BeforeEach
    fun clean() {
        jdbcTemplate.execute("ALTER TABLE popup ALTER COLUMN view_count SET DEFAULT 0")
        jdbcTemplate.update("INSERT INTO interest_category (interest_category_id, category) VALUES (1, '캐릭터/IP') ON CONFLICT DO NOTHING")
        jdbcTemplate.update("INSERT INTO favorite_area (favorite_area_id, area) VALUES (1, '성수') ON CONFLICT DO NOTHING")
        popupRepository.deleteAllInBatch()
    }

    private fun popup(
        name: String,
        endDate: LocalDate?,
        enrichedDaysAgo: Long?,
        areaId: Int? = 1,
        retryCount: Int = 0,
        imageUrls: List<String>? = null,
        imageCheckedDaysAgo: Long? = null,
        brand: String? = "브랜드",
    ): Long =
        popupRepository
            .saveAndFlush(
                PopupEntity(
                    source = SourceType.KAKAO_MAP,
                    externalId = name,
                    title = name,
                    brand = brand,
                    interestCategoryId = 1,
                    areaId = areaId,
                    startDate = today.minusDays(60),
                    endDate = endDate,
                    enrichRetryCount = retryCount,
                    enrichedAt = enrichedDaysAgo?.let { now.minusDays(it) },
                    imageUrls = imageUrls,
                    imageCheckedAt = imageCheckedDaysAgo?.let { now.minusDays(it) },
                ),
            ).id!!

    @Test
    fun `보강 대상은 미보강 · 공백 재시도 · 진행 중 갱신의 합집합이고 enriched_at 오래된 순(미보강 먼저)이다`() {
        val unenriched = popup("미보강", endDate = null, enrichedDaysAgo = null)
        val ongoingDue = popup("진행중-15일", endDate = today.plusDays(30), enrichedDaysAgo = 15)
        popup("진행중-10일", endDate = today.plusDays(30), enrichedDaysAgo = 10)
        val imminentDue = popup("임박-4일", endDate = today.plusDays(5), enrichedDaysAgo = 4)
        popup("임박-2일", endDate = today.plusDays(5), enrichedDaysAgo = 2)
        val endsToday = popup("오늘종료-4일", endDate = today, enrichedDaysAgo = 4)
        popup("종료-30일", endDate = today.minusDays(1), enrichedDaysAgo = 30)
        popup("종료일없음-30일", endDate = null, enrichedDaysAgo = 30, retryCount = 2)
        val retry = popup("area공백-8일", endDate = today.minusDays(1), enrichedDaysAgo = 8, areaId = null)
        val ongoingRetryExhausted = popup("진행중-한도소진-20일", endDate = today.plusDays(30), enrichedDaysAgo = 20, areaId = null, retryCount = 2)

        val targets = popupRepository.findEnrichTargets(criteria).map { it.id }

        targets shouldContainExactly listOf(unenriched, ongoingRetryExhausted, ongoingDue, retry, imminentDue, endsToday)
    }

    @Test
    fun `limit 안에서는 미보강 팝업이 항상 먼저다`() {
        popup("진행중-30일", endDate = today.plusDays(30), enrichedDaysAgo = 30)
        val first = popup("미보강1", endDate = null, enrichedDaysAgo = null)
        val second = popup("미보강2", endDate = null, enrichedDaysAgo = null)

        popupRepository.findEnrichTargets(criteria.copy(limit = 2)).map { it.id } shouldContainExactly listOf(first, second)
    }

    @Test
    fun `이미지 대상은 이미지가 비고 보강된 노출 후보 중 재시도 간격이 지난 팝업이다`() {
        val nullImages = popup("이미지null", endDate = today.plusDays(10), enrichedDaysAgo = 1)
        val emptyImages = popup("이미지빈배열", endDate = null, enrichedDaysAgo = 1, imageUrls = emptyList())
        val retryDue = popup("재시도-8일", endDate = today, enrichedDaysAgo = 1, imageCheckedDaysAgo = 8)
        popup("이미지있음", endDate = today.plusDays(10), enrichedDaysAgo = 1, imageUrls = listOf("https://img/1.jpg"))
        popup("미보강", endDate = today.plusDays(10), enrichedDaysAgo = null)
        popup("브랜드·설명없음", endDate = today.plusDays(10), enrichedDaysAgo = 1, brand = null)
        popup("종료", endDate = today.minusDays(1), enrichedDaysAgo = 1)
        popup("재시도-2일", endDate = today.plusDays(10), enrichedDaysAgo = 1, imageCheckedDaysAgo = 2)

        popupRepository.findImageTargets(today, now.minusDays(7), 100).map { it.id } shouldContainExactly
            listOf(nullImages, emptyImages, retryDue)
    }
}
