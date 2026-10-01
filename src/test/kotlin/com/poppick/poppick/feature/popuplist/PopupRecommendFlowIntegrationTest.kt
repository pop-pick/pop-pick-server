package com.poppick.poppick.feature.popuplist

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.global.util.KST
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
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
import java.time.LocalDate
import kotlin.random.Random

/**
 * 로컬 전용 Postgres 에 회원 선호값(member_interest_category · member_favorite_area)과 팝업을 만들어
 * 회원 추천 전체 흐름(선호 조회 → 카테고리 · 지역 후보 → 3개 미만이면 인기 팝업으로 채움 · 중복 제거 → 3개)을 확인한다.
 * **공유 DB 에는 절대 연결하지 않는다.** 실행 조건 · 준비는 PopupViewCountIntegrationTest 와 같다.
 *
 * 인기 팝업(전체 조회수 1~3위)을 이 테스트가 정하도록 PopupListSortIntegrationTest(1_000_00x)보다 큰 조회수를 준다.
 * 같은 DB 를 쓰는 다른 테스트의 인기 Top3 검증을 깨지 않도록, 끝나면 만든 팝업 · 선호값을 지운다.
 * 카테고리 · 지역 id 는 실제로 없는 9200 · 9300 번대를 쓴다.
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
class PopupRecommendFlowIntegrationTest {
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
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var popupWriter: PopupWriter

    @Autowired
    lateinit var popupListService: PopupListService

    private val today = LocalDate.now(KST)
    private val marker = "recflow${Random.nextInt(100_000, 999_999)}"
    private val ids = mutableMapOf<String, Long>()
    private val memberKeys = mutableListOf<String>()

    @BeforeAll
    fun seed() {
        jdbcTemplate.execute("ALTER TABLE popup ALTER COLUMN view_count SET DEFAULT 0")

        fun seed(
            name: String,
            categoryId: Int?,
            areaId: Int?,
            viewCount: Long,
            endDate: LocalDate? = null,
        ) {
            val popup =
                Popup(
                    source = SourceType.PERPLEXITY,
                    title = "$marker $name",
                    startDate = today.minusDays(1),
                    endDate = endDate,
                    interestCategoryId = categoryId,
                    areaId = areaId,
                )
            val id = popupWriter.save(popup).id!!
            jdbcTemplate.update("UPDATE popup SET view_count = ? WHERE popup_id = ?", viewCount, id)
            ids[name] = id
        }

        // 인기 팝업: 전체 조회수 1~4위. 인기 Top3 = popularFirst, popularPreferred, popularThird
        seed("popularFirst", 9290, null, viewCount = 2_000_005)
        seed("popularPreferred", 9202, null, viewCount = 2_000_004) // 인기 Top3 이면서 카테고리 9202 회원의 추천 후보
        seed("popularThird", 9290, null, viewCount = 2_000_003)
        seed("popularFourth", 9290, null, viewCount = 2_000_002)
        seed("endedPopular", 9201, 9301, viewCount = 2_000_009, endDate = today.minusDays(1)) // 종료라 어디에도 안 나온다

        // 취향 후보
        seed("categoryHigh", 9201, null, viewCount = 10)
        seed("categoryAndArea", 9201, 9301, viewCount = 7)
        seed("areaNullCategory", null, 9301, viewCount = 5)
        seed("categoryLow", 9201, null, viewCount = 1)
        seed("otherCategory", 9203, null, viewCount = 4)
        seed("otherArea", 9208, 9302, viewCount = 3)
    }

    @AfterAll
    fun cleanUp() {
        ids.values.forEach { jdbcTemplate.update("DELETE FROM popup WHERE popup_id = ?", it) }
        memberKeys.forEach {
            jdbcTemplate.update("DELETE FROM member_interest_category WHERE member_key = ?", it)
            jdbcTemplate.update("DELETE FROM member_favorite_area WHERE member_key = ?", it)
        }
    }

    /** 온보딩 선택값을 가진 회원. member_* 선호 테이블에만 넣는다(MemberPreferenceReader 가 memberKey 로 읽는다). */
    private fun member(
        name: String,
        categoryIds: List<Int> = emptyList(),
        areaIds: List<Int> = emptyList(),
    ): String {
        val memberKey = "$marker-$name"
        categoryIds.forEach {
            jdbcTemplate.update("INSERT INTO member_interest_category (member_key, interest_category_id) VALUES (?, ?)", memberKey, it)
        }
        areaIds.forEach {
            jdbcTemplate.update("INSERT INTO member_favorite_area (member_key, favorite_area_id) VALUES (?, ?)", memberKey, it)
        }
        memberKeys += memberKey
        return memberKey
    }

    private fun recommend(memberKey: String) = popupListService.findRecommendedPopups(memberKey).map { it.id!! }

    private fun idsOf(vararg names: String) = names.map { ids.getValue(it) }

    @Test
    fun `후보가 3개 미만이면 인기 팝업으로 채우고, 이미 담긴 인기 팝업은 빼고 다음 순위로 채워 3개를 돌려준다`() {
        // 후보: popularPreferred(카테고리 9202) · otherArea(지역 9302)
        val memberKey = member("partial", categoryIds = listOf(9202), areaIds = listOf(9302))
        val viewCountsBefore = jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id")

        val result = recommend(memberKey)

        // 후보 2개(인기순) + 인기 Top3 에서 popularPreferred 를 뺀 첫 번째(popularFirst)
        result shouldBe idsOf("popularPreferred", "otherArea", "popularFirst")
        result.toSet().size shouldBe 3
        jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id") shouldBe viewCountsBefore
    }

    @Test
    fun `후보가 1개면 인기 Top3 앞에서 2개를 채운다`() {
        val memberKey = member("single", categoryIds = listOf(9203))

        recommend(memberKey) shouldBe idsOf("otherCategory", "popularFirst", "popularPreferred")
    }

    @Test
    fun `후보가 3개 이상이면 관심 카테고리 OR 선호 지역 후보만 인기순 3개(종료 팝업 제외, 카테고리 NULL 이어도 지역이 맞으면 포함)`() {
        // 후보: categoryHigh(10) · categoryAndArea(7) · areaNullCategory(5) · categoryLow(1), endedPopular 는 종료
        val memberKey = member("full", categoryIds = listOf(9201), areaIds = listOf(9301))

        recommend(memberKey) shouldBe idsOf("categoryHigh", "categoryAndArea", "areaNullCategory")
    }

    @Test
    fun `선호값이 없거나 맞는 팝업이 없으면 인기 Top3 와 같다`() {
        val popularTop3 = popupListService.findPopularPopups().map { it.id!! }
        popularTop3 shouldBe idsOf("popularFirst", "popularPreferred", "popularThird")

        recommend(member("empty")) shouldBe popularTop3
        recommend(member("noMatch", categoryIds = listOf(9299), areaIds = listOf(9399))) shouldBe popularTop3
    }
}
