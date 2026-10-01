package com.poppick.poppick.feature.popuplist

import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupMapResponse
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.util.KST
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
import java.time.LocalDate
import kotlin.random.Random

/**
 * 로컬 전용 Postgres 에 좌표가 다른 팝업을 만들어 지도 조회(영역 · keyword · 이름 매핑 · 조회수 불변)를 확인한다.
 * **공유 DB 에는 절대 연결하지 않는다.** 실행 조건 · 준비는 PopupViewCountIntegrationTest 와 같다.
 * 다른 테스트 데이터와 섞이지 않도록 서울과 먼 좌표(위도 10 · 경도 100 부근)에 테스트 팝업을 둔다.
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
class PopupMapIntegrationTest {
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
    private val keyword = "mapit${Random.nextInt(100_000, 999_999)}"
    private val bounds = MapBounds(10.000, 100.000, 10.010, 100.010)
    private val ids = mutableMapOf<String, Long>()

    @BeforeAll
    fun seed() {
        jdbcTemplate.execute("ALTER TABLE popup ALTER COLUMN view_count SET DEFAULT 0")
        jdbcTemplate.update("INSERT INTO interest_category (interest_category_id, category) VALUES (1, '캐릭터/IP') ON CONFLICT DO NOTHING")
        jdbcTemplate.update("INSERT INTO favorite_area (favorite_area_id, area) VALUES (1, '성수') ON CONFLICT DO NOTHING")

        fun seed(
            name: String,
            lat: Double?,
            lng: Double?,
            title: String = "지도 테스트 $name",
            startDate: LocalDate? = today.minusDays(1),
            endDate: LocalDate? = null,
            categoryId: Int? = null,
            areaId: Int? = null,
            viewCount: Long = 0,
        ) {
            val popup =
                Popup(
                    source = SourceType.PERPLEXITY,
                    title = title,
                    latitude = lat,
                    longitude = lng,
                    startDate = startDate,
                    endDate = endDate,
                    interestCategoryId = categoryId,
                    areaId = areaId,
                )
            val id = popupWriter.save(popup).id!!
            jdbcTemplate.update("UPDATE popup SET view_count = ? WHERE popup_id = ?", viewCount, id)
            ids[name] = id
        }

        seed("inside", 10.005, 100.005, title = "지도 테스트 inside $keyword", categoryId = 1, areaId = 1, viewCount = 3)
        seed("edgeSw", 10.000, 100.000, viewCount = 1) // 남서 모서리 정확히
        seed("edgeNe", 10.010, 100.010, title = "지도 테스트 edgeNe $keyword", viewCount = 2) // 북동 모서리 정확히
        seed("outsideLat", 10.011, 100.005, title = "지도 테스트 outsideLat $keyword")
        seed("outsideLng", 10.005, 99.999)
        seed("noCoords", null, null, title = "지도 테스트 noCoords $keyword")
        seed("ended", 10.005, 100.005, title = "지도 테스트 ended $keyword", endDate = today.minusDays(1))
        seed("notOpened", 10.005, 100.005, title = "지도 테스트 notOpened $keyword", startDate = today.plusDays(1))
        seed("openedToday", 10.006, 100.006, startDate = today)
    }

    private fun idsOf(popups: List<Popup>) = popups.map { it.id!! }

    @Test
    fun `keyword 없으면 영역 안(경계 포함)의 노출 팝업만 인기순으로 돌려준다`() {
        val result = popupListService.findMapPopups(null, bounds)

        // view_count: inside 3, edgeNe 2, edgeSw 1, openedToday 0
        idsOf(result) shouldBe listOf("inside", "edgeNe", "edgeSw", "openedToday").map { ids.getValue(it) }
    }

    @Test
    fun `keyword 가 있으면 영역 안 AND 목록과 같은 keyword 조건`() {
        val result = popupListService.findMapPopups(keyword, bounds)

        idsOf(result) shouldBe listOf("inside", "edgeNe").map { ids.getValue(it) }
        result.forEach { it.title.contains(keyword) shouldBe true }
    }

    @Test
    fun `지도 결과는 같은 keyword 의 목록 결과 중 영역 안 좌표를 가진 팝업과 같다`() {
        val listed =
            popupListService
                .findPopups(null, keyword, null, PopupSortType.POPULAR, Cursorable(null, 50))
                .map { it.popup }
                .content
                .filter { p ->
                    p.latitude != null &&
                        p.longitude != null &&
                        p.latitude in bounds.swLat..bounds.neLat &&
                        p.longitude in bounds.swLng..bounds.neLng
                }

        idsOf(popupListService.findMapPopups(keyword, bounds)) shouldBe idsOf(listed)
    }

    @Test
    fun `영역 안에 팝업이 없으면 빈 목록`() {
        popupListService.findMapPopups(null, MapBounds(-10.0, -100.0, -9.99, -99.99)) shouldBe emptyList()
    }

    @Test
    fun `카테고리 · 상권 id 와 이름을 붙이고, 없으면 null 이다`() {
        val result = popupListService.findMapPopups(null, bounds)
        val categoryNames = popupListService.findCategoryNames()
        val areaNames = popupListService.findAreaNames()
        val responses = result.associate { it.id!! to PopupMapResponse.from(it, categoryNames, areaNames) }

        with(responses.getValue(ids.getValue("inside"))) {
            interestCategoryId shouldBe 1
            interestCategoryName shouldBe "캐릭터/IP"
            areaId shouldBe 1
            areaName shouldBe "성수"
            latitude shouldBe 10.005
            longitude shouldBe 100.005
        }
        with(responses.getValue(ids.getValue("edgeSw"))) {
            interestCategoryName shouldBe null
            areaId shouldBe null
            areaName shouldBe null
        }
    }

    @Test
    fun `지도 조회 전후 조회수는 변하지 않는다`() {
        val before = jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id")

        popupListService.findMapPopups(null, bounds)
        popupListService.findMapPopups(keyword, bounds)

        jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id") shouldBe before
    }
}
