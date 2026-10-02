package com.poppick.poppick.feature.popuplist

import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter
import com.poppick.poppick.feature.popupdetail.business.PopupDetailService
import com.poppick.poppick.feature.popupdetail.presentation.dto.response.PopupDetailResponse
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupListResponse
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupMapResponse
import com.poppick.poppick.global.paging.Cursorable
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
 * 로컬 전용 Postgres 에 상권(area_id)이 다른 팝업을 만들어 지역 검색(keyword 상권 이름) · 목록 지역 필터(areaId) · 지역 뱃지를 확인한다.
 * **공유 DB 에는 절대 연결하지 않는다.** 실행 조건 · 준비는 PopupViewCountIntegrationTest 와 같다.
 * keyword 상권 이름 검색은 DB 전체 팝업을 대상으로 하므로, 결과는 이 테스트가 만든 팝업으로 좁혀 비교한다.
 * 지도 확인용 좌표는 다른 테스트와 섞이지 않는 위도 20 · 경도 110 부근에 둔다. 끝나면 만든 팝업을 지운다.
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
class PopupAreaIntegrationTest {
    companion object {
        private val LOCAL_HOSTS = listOf("//localhost:", "//127.0.0.1:")

        private const val SEONGSU = 1
        private const val HONGDAE = 3

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

    @Autowired
    lateinit var popupDetailService: PopupDetailService

    private val today = LocalDate.now(KST)
    private val marker = "areait${Random.nextInt(100_000, 999_999)}"
    private val bounds = MapBounds(20.000, 110.000, 20.010, 110.010)
    private val ids = mutableMapOf<String, Long>()

    @BeforeAll
    fun seed() {
        jdbcTemplate.execute("ALTER TABLE popup ALTER COLUMN view_count SET DEFAULT 0")
        jdbcTemplate.update("INSERT INTO favorite_area (favorite_area_id, area) VALUES (?, '성수') ON CONFLICT DO NOTHING", SEONGSU)
        jdbcTemplate.update("INSERT INTO favorite_area (favorite_area_id, area) VALUES (?, '홍대') ON CONFLICT DO NOTHING", HONGDAE)

        fun seed(
            name: String,
            areaId: Int?,
            viewCount: Long,
            title: String = "$marker $name",
            brand: String? = null,
            addressRoad: String? = "서울 마포구 양화로 186",
            addressJibun: String? = "서울 마포구 동교동 167-2",
            startDate: LocalDate? = today.minusDays(1),
            endDate: LocalDate? = null,
            lat: Double? = null,
            lng: Double? = null,
        ) {
            val popup =
                Popup(
                    source = SourceType.PERPLEXITY,
                    title = title,
                    brand = brand,
                    addressRoad = addressRoad,
                    addressJibun = addressJibun,
                    startDate = startDate,
                    endDate = endDate,
                    areaId = areaId,
                    latitude = lat,
                    longitude = lng,
                )
            val id = popupWriter.save(popup).id!!
            jdbcTemplate.update("UPDATE popup SET view_count = ? WHERE popup_id = ?", viewCount, id)
            ids[name] = id
        }

        // 주소 · 이름 어디에도 "홍대" 가 없고 area_id 만 홍대
        seed("hongdaeCafe", HONGDAE, viewCount = 5, startDate = today.minusDays(3), lat = 20.005, lng = 110.005)
        seed("hongdaeCharacter", HONGDAE, viewCount = 4, title = "$marker 캐릭터 팝업", startDate = today.minusDays(2))
        // 상권 분류 없음, 주소에만 "홍대"
        seed("addressOnly", null, viewCount = 3, addressRoad = "서울 마포구 홍대입구역 앞", lat = 20.006, lng = 110.006)
        seed("seongsuCharacter", SEONGSU, viewCount = 2, title = "$marker 캐릭터 굿즈", brand = "brand$marker", addressRoad = "서울 성동구 연무장길 10")
        seed("noArea", null, viewCount = 1, lat = 20.007, lng = 110.007)
        seed("hongdaeEnded", HONGDAE, viewCount = 9, endDate = today.minusDays(1), lat = 20.005, lng = 110.005)
    }

    @AfterAll
    fun cleanUp() {
        ids.values.forEach { jdbcTemplate.update("DELETE FROM popup WHERE popup_id = ?", it) }
    }

    /** DB 전체 결과에서 이 테스트가 만든 팝업만 순서대로. */
    private fun seeded(popups: List<Popup>) = popups.mapNotNull { p -> ids.entries.firstOrNull { it.value == p.id }?.key }

    private fun list(
        keyword: String?,
        areaId: Int? = null,
        sort: PopupSortType = PopupSortType.POPULAR,
    ) = seeded(popupListService.findPopups(null, keyword, areaId, sort, Cursorable(null, 50)).map { it.popup }.content)

    @Test
    fun `keyword 가 상권 이름이면 주소에 그 이름이 없어도 그 상권 팝업을 찾고, 주소 일치 팝업도 그대로 찾는다`() {
        list("홍대") shouldBe listOf("hongdaeCafe", "hongdaeCharacter", "addressOnly")
    }

    @Test
    fun `상권 이름 부분 일치(홍)도 찾는다`() {
        list("홍") shouldBe listOf("hongdaeCafe", "hongdaeCharacter", "addressOnly")
    }

    @Test
    fun `기존 이름 · 브랜드 · 주소 검색은 그대로다`() {
        list(marker) shouldBe listOf("hongdaeCafe", "hongdaeCharacter", "addressOnly", "seongsuCharacter", "noArea")
        list("brand$marker") shouldBe listOf("seongsuCharacter")
        list("홍대입구역") shouldBe listOf("addressOnly") // 상권 이름("홍대")은 "홍대입구역" 을 포함하지 않는다
        list("연무장길") shouldBe listOf("seongsuCharacter")
    }

    @Test
    fun `areaId 지역 필터는 그 상권 팝업만(종료 · 상권 없음 제외)`() {
        list(null, areaId = HONGDAE) shouldBe listOf("hongdaeCafe", "hongdaeCharacter")
        list(null, areaId = SEONGSU) shouldBe listOf("seongsuCharacter")
        list(null, areaId = 9999) shouldBe emptyList()
    }

    @Test
    fun `areaId 와 keyword 는 AND`() {
        list("캐릭터", areaId = HONGDAE) shouldBe listOf("hongdaeCharacter")
        list("캐릭터", areaId = SEONGSU) shouldBe listOf("seongsuCharacter")
        list("홍대", areaId = SEONGSU) shouldBe emptyList()
    }

    @Test
    fun `areaId 필터에서도 최신순 cursor 로 빠짐 · 중복 없이 이어서 조회한다`() {
        val collected = mutableListOf<Popup>()
        var cursor: PopupSearchCursor? = null
        do {
            val slice = popupListService.findPopups(null, marker, HONGDAE, PopupSortType.LATEST, Cursorable(cursor, 1)).map { it.popup }
            collected += slice.content
            cursor = slice.content.lastOrNull()?.let { PopupSearchCursor.of(it, PopupSortType.LATEST) }
        } while (slice.hasNext)

        // start_date DESC: hongdaeCharacter(2일 전) → hongdaeCafe(3일 전)
        seeded(collected) shouldBe listOf("hongdaeCharacter", "hongdaeCafe")
    }

    @Test
    fun `지도 keyword 도 상권 이름으로 찾는다(영역 안 · 노출 중만)`() {
        seeded(popupListService.findMapPopups("홍대", bounds)) shouldBe listOf("hongdaeCafe", "addressOnly")
        seeded(popupListService.findMapPopups(null, bounds)) shouldBe listOf("hongdaeCafe", "addressOnly", "noArea")
    }

    @Test
    fun `목록 · 지도 · 상세 응답에 지역 id 와 이름을 담고, 상권이 없으면 둘 다 null`() {
        val areaNames = popupListService.findAreaNames()
        val categoryNames = popupListService.findCategoryNames()
        val listed =
            popupListService
                .findPopups(null, marker, null, PopupSortType.POPULAR, Cursorable(null, 50))
                .map {
                    it.popup
                }.content
                .associateBy { it.id }

        with(PopupListResponse.from(listed.getValue(ids.getValue("hongdaeCafe")), wished = false, categoryNames, areaNames)) {
            areaId shouldBe HONGDAE
            areaName shouldBe "홍대"
        }
        with(PopupListResponse.from(listed.getValue(ids.getValue("noArea")), wished = false, categoryNames, areaNames)) {
            areaId shouldBe null
            areaName shouldBe null
        }

        val mapped = popupListService.findMapPopups(null, bounds).associateBy { it.id }
        PopupMapResponse.from(mapped.getValue(ids.getValue("hongdaeCafe")), categoryNames, areaNames).areaName shouldBe "홍대"
        PopupMapResponse.from(mapped.getValue(ids.getValue("noArea")), categoryNames, areaNames).areaName shouldBe null

        val detailAreaNames = popupDetailService.findAreaNames()
        detailAreaNames shouldBe areaNames
        with(PopupDetailResponse.from(listed.getValue(ids.getValue("hongdaeCharacter")), wished = false, categoryNames, detailAreaNames)) {
            areaId shouldBe HONGDAE
            areaName shouldBe "홍대"
        }
    }

    @Test
    fun `지역 검색 · 필터 조회 전후 조회수는 변하지 않는다`() {
        val before = jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id")

        list("홍대", areaId = HONGDAE)
        popupListService.findMapPopups("홍대", bounds)

        jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id") shouldBe before
    }
}
