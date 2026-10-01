package com.poppick.poppick.feature.popuplist

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter
import com.poppick.poppick.feature.popupdetail.business.PopupDetailService
import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.util.KST
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.LocalDate
import kotlin.random.Random

/**
 * 로컬 전용 Postgres 에 조회수가 다른 팝업을 만들어 인기순 정렬 · cursor · 상세 조회수 반영을 확인한다.
 * **공유 DB 에는 절대 연결하지 않는다.** 실행 조건 · 준비는 PopupViewCountIntegrationTest 와 같다.
 * - POPUP_VIEW_TEST_DB_URL 이 설정된 경우에만 실행되고, localhost · 127.0.0.1 이 아닌 URL 이면 즉시 실패한다.
 * - 상세 조회수 테스트는 로컬 Redis(POPUP_VIEW_TEST_REDIS_HOST)가 있을 때만 실행하고, 만든 조회 메모는 지운다.
 * - 다른 테스트 데이터와 섞이지 않도록 이 테스트의 팝업 제목에 고유 표식을 넣고 그 표식으로 검색한다.
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
class PopupListSortIntegrationTest {
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

    @Autowired
    lateinit var popupDetailService: PopupDetailService

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    private val marker = "sortit${Random.nextInt(100_000, 999_999)}"
    private val today = LocalDate.now(KST)
    private val ids = mutableMapOf<String, Long>()

    /** 이름 → (오픈일, 종료일, 조회수). 노출 대상은 A~E, F(종료) · G(오픈 전)는 제외 대상. */
    private val seeds =
        linkedMapOf(
            "A" to Triple(today.minusDays(5), null, 5L),
            "B" to Triple(today.minusDays(1), null, 3L),
            "C" to Triple(today.minusDays(3), null, 3L),
            "D" to Triple(today, null, 0L),
            "E" to Triple(null, null, 0L),
            "F" to Triple(today.minusDays(10), today.minusDays(1), 9L),
            "G" to Triple(today.plusDays(1), null, 7L),
        )

    @BeforeAll
    fun seed() {
        jdbcTemplate.execute("ALTER TABLE popup ALTER COLUMN view_count SET DEFAULT 0")
        seeds.forEach { (name, seed) ->
            val (startDate, endDate, viewCount) = seed
            val id =
                popupWriter
                    .save(Popup(source = SourceType.PERPLEXITY, title = "$marker $name", startDate = startDate, endDate = endDate))
                    .id!!
            jdbcTemplate.update("UPDATE popup SET view_count = ? WHERE popup_id = ?", viewCount, id)
            ids[name] = id
        }
    }

    @AfterAll
    fun cleanRedis() {
        if (System.getenv("POPUP_VIEW_TEST_REDIS_HOST") == null) return
        ids.values.forEach { id ->
            redisTemplate.keys("popup:view:$id:*").takeIf { it.isNotEmpty() }?.let { redisTemplate.delete(it) }
        }
    }

    private fun names(popups: List<Popup>) = popups.map { it.title.removePrefix("$marker ") }

    private fun fetchAll(
        sort: PopupSortType,
        limit: Int,
    ): List<Popup> {
        val seen = mutableListOf<Popup>()
        var cursor: PopupSearchCursor? = null
        do {
            val slice = popupListService.findPopups(marker, null, sort, Cursorable(cursor, limit))
            seen += slice.content
            cursor = slice.content.lastOrNull()?.let { PopupSearchCursor.of(it, sort) }
        } while (slice.hasNext && cursor != null)
        return seen
    }

    /** 같은 조회수(B · C = 3, D · E = 0)는 popupId 가 큰(나중에 만든) 쪽이 앞. */
    private fun expectedPopular() =
        listOf("A") + listOf("B", "C").sortedByDescending { ids[it] } + listOf("D", "E").sortedByDescending { ids[it] }

    @Test
    fun `인기순은 조회수 내림차순 · 같은 조회수는 popupId 내림차순이고 종료 · 오픈 전 팝업은 제외한다`() {
        val page = popupListService.findPopups(marker, null, PopupSortType.POPULAR, Cursorable(null, 10))

        names(page.content) shouldBe expectedPopular()
        page.content.map { it.viewCount } shouldBe listOf(5L, 3L, 3L, 0L, 0L)
        page.hasNext shouldBe false
    }

    @Test
    fun `인기순 cursor 로 limit 2 씩 넘겨도 한 번에 조회한 결과와 같다(누락 · 중복 없음)`() {
        names(fetchAll(PopupSortType.POPULAR, limit = 2)) shouldBe expectedPopular()
    }

    @Test
    fun `인기순 첫 페이지 nextCursor 위치는 (조회수, popupId) 이고 그 다음부터 이어진다`() {
        val first = popupListService.findPopups(marker, null, PopupSortType.POPULAR, Cursorable(null, 2))
        val cursor = PopupSearchCursor.Popular.of(first.content.last())
        val second = popupListService.findPopups(marker, null, PopupSortType.POPULAR, Cursorable(cursor, 2))

        first.hasNext shouldBe true
        cursor.viewCount shouldBe 3L
        names(first.content + second.content) shouldBe expectedPopular().take(4)
    }

    @Test
    fun `최신순은 기존대로 오픈일 최신순 · 오픈일 없음은 맨 뒤이며 cursor 로 이어도 같다`() {
        val expected = listOf("D", "B", "C", "A", "E")

        names(popupListService.findPopups(marker, null, PopupSortType.LATEST, Cursorable(null, 10)).content) shouldBe expected
        names(fetchAll(PopupSortType.LATEST, limit = 2)) shouldBe expected
    }

    @Test
    fun `지금 인기 있는 팝업은 노출 대상 중 조회수 상위 3개(같은 조회수는 popupId DESC)이고 조회수를 올리지 않는다`() {
        // 다른 테스트 데이터보다 조회수를 크게 줘서 전체 1~3위가 되게 한다. 정렬 검증용 표식은 넣지 않는다.
        fun seedTop(
            name: String,
            startDate: LocalDate?,
            endDate: LocalDate?,
            viewCount: Long,
        ): Long {
            val id =
                popupWriter
                    .save(
                        Popup(source = SourceType.PERPLEXITY, title = "top3 $name", startDate = startDate, endDate = endDate),
                    ).id!!
            jdbcTemplate.update("UPDATE popup SET view_count = ? WHERE popup_id = ?", viewCount, id)
            return id
        }
        val openedToday = seedTop("오늘 오픈", today, null, 1_000_005)
        val older = seedTop("같은 조회수 먼저 등록", today.minusDays(2), null, 1_000_003)
        val newer = seedTop("같은 조회수 나중 등록", null, null, 1_000_003)
        val ended = seedTop("종료", today.minusDays(10), today.minusDays(1), 1_000_009)
        val notYetOpened = seedTop("오픈 전", today.plusDays(1), null, 1_000_007)
        val viewCountsBefore = jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id")

        val top3 = popupListService.findPopularPopups()

        top3.map { it.id } shouldBe listOf(openedToday, newer, older)
        top3.map { it.id }.none { it == ended || it == notYetOpened } shouldBe true
        top3.map { it.id } shouldBe
            popupListService.findPopups(null, null, PopupSortType.POPULAR, Cursorable(null, 3)).content.map { it.id }
        jdbcTemplate.queryForList("SELECT popup_id, view_count FROM popup ORDER BY popup_id") shouldBe viewCountsBefore
    }

    @Test
    fun `상세 조회는 같은 조회자 10분 내 재조회를 세지 않고 다른 조회자는 센다(Redis + DB)`() {
        assumeTrue(System.getenv("POPUP_VIEW_TEST_REDIS_HOST") != null, "로컬 Redis 없음")
        // 정렬 검증용 팝업과 섞이지 않도록 표식 없는 팝업을 따로 만든다
        val popupId = popupWriter.save(Popup(source = SourceType.PERPLEXITY, title = "상세 조회수 확인용")).id!!
        ids["detail"] = popupId
        val member = PopupViewer.member("$marker-member")
        val anonymous = PopupViewer.anonymous("203.0.113.7", null, "127.0.0.1", "$marker-ua")

        popupDetailService.findPopupDetail(popupId, member).viewCount shouldBe 1L
        popupDetailService.findPopupDetail(popupId, member).viewCount shouldBe 1L
        popupDetailService.findPopupDetail(popupId, anonymous).viewCount shouldBe 2L
        popupDetailService.findPopupDetail(popupId, anonymous).viewCount shouldBe 2L

        jdbcTemplate.queryForObject("SELECT view_count FROM popup WHERE popup_id = ?", Long::class.java, popupId) shouldBe 2L
    }
}
