package com.poppick.poppick.feature.popuplist

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupSearchRepository
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.util.KST
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

/**
 * 실제 DB 의 popup 데이터로 PopupListService 를 호출해 목록 조회와 검색 동작을 확인한다.
 * 조회 전용 테스트이며 데이터를 생성 · 수정 · 삭제하지 않는다.
 * DB_URL, DB_USERNAME, DB_PASSWORD 환경변수가 모두 설정된 경우에만 실행된다.
 * 정렬은 오픈일 최신순(start_date DESC NULLS LAST, popup_id DESC)이고, 오픈 전(start_date > today) · 종료(end_date < today) 팝업은 제외한다.
 * cursor 는 이전 페이지 마지막 팝업의 (startDate, popupId) 값이며, 그 row 가 DB 에 없어도 값 기준으로 이어서 조회한다.
 * 검색어는 POPUP_TEST_KEYWORD 로 지정할 수 있으며, 지정하지 않으면 첫 번째 팝업의 제목을 사용한다.
 */
@EnabledIfEnvironmentVariables(
    EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+"),
    EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+"),
    EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+"),
)
@SpringBootTest(
    properties = [
        // 테스트 중 수집 배치가 실행되지 않도록 비활성화
        "collection.cron=-",
        // 공유 DB 커넥션 풀을 과하게 점유하지 않도록 최소한의 커넥션만 사용
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0",
        // 조회와 무관한 외부 연동 설정은 더미 값으로 대체
        "kakao.api-key=unused",
        "perplexity.api-key=unused",
        "openai.api-key=unused",
        "google.client-id=unused",
        "google.client-secret=unused",
        "jwt.secret-key=dW51c2VkdW51c2VkdW51c2VkdW51c2VkdW51c2VkdW51c2VkdW51c2Vk",
    ],
)
@ActiveProfiles("local")
class PopupListIntegrationTest {
    @Autowired
    lateinit var popupListService: PopupListService

    @Autowired
    lateinit var popupSearchRepository: PopupSearchRepository

    private val today: LocalDate get() = LocalDate.now(KST)

    /** 오픈일 최신순. 오픈일이 없으면 맨 뒤, 같은 오픈일은 popupId 내림차순. */
    private val latestOpenOrder: Comparator<Popup> =
        compareBy<Popup, LocalDate?>(nullsLast(reverseOrder())) { it.startDate }.thenByDescending { it.id }

    @Test
    fun `keyword 없이 조회하면 노출 대상 팝업을 오픈일 최신순으로 반환한다`() {
        val slice = findPopups(null, Cursorable(null, 20))

        printPage("keyword 없음, limit=20", slice.content, slice.hasNext)
        assumeTrue(slice.content.isNotEmpty(), "노출 중인 팝업이 없음")

        slice.content.size shouldBeLessThanOrEqual 20
        slice.content shouldBe slice.content.sortedWith(latestOpenOrder)
        slice.content.forEach { it.isVisible() shouldBe true }
    }

    @Test
    fun `limit 만큼만 반환하고 남은 데이터가 있으면 hasNext 가 true 다`() {
        val all = findPopups(null, Cursorable(null, 50))
        val slice = findPopups(null, Cursorable(null, 3))

        printPage("limit=3", slice.content, slice.hasNext)

        slice.content.size shouldBe minOf(3, all.content.size)
        slice.hasNext shouldBe (all.content.size > 3)
    }

    @Test
    fun `첫 페이지 마지막 팝업을 cursor 로 넘기면 정렬 순서상 그 다음부터 이어서 조회한다`() {
        val first = findPopups(null, Cursorable(null, 3))
        assumeTrue(first.hasNext, "다음 페이지가 없음(노출 중 팝업 3개 이하)")

        val cursor = PopupSearchCursor.of(first.content.last())
        val second = findPopups(null, Cursorable(cursor, 3))

        printPage("1페이지 limit=3", first.content, first.hasNext)
        printPage("2페이지 cursor=$cursor limit=3", second.content, second.hasNext)

        second.content.forEach { latestOpenOrder.compare(first.content.last(), it) shouldBeLessThan 0 }
        (first.content.map { it.id } intersect second.content.map { it.id }.toSet()) shouldBe emptySet()

        // 두 페이지를 이어 붙인 결과는 limit=6 으로 한 번에 조회한 결과와 같아야 한다
        val combined = findPopups(null, Cursorable(null, 6))
        (first.content + second.content).map { it.id } shouldBe combined.content.map { it.id }
    }

    @Test
    fun `cursor 로 끝까지 조회하면 노출 대상 전체를 누락 · 중복 없이 오픈일 최신순으로 반환한다`() {
        // 같은 오픈일 · 오픈일 없음 구간에서 페이지가 끊기도록 작은 limit 으로 순회한다
        val seen = fetchAll(limit = 7)
        val expected = visibleInOrder()

        println("[전체 순회] 노출 팝업 ${seen.size}건, 오픈일 없음 ${seen.count { it.startDate == null }}건, today=$today")
        seen.map { it.id } shouldBe expected.map { it.id }
    }

    @Test
    fun `오늘 오픈한 팝업은 포함하고 오픈 전 · 종료된 팝업은 제외한다`() {
        val seenIds = fetchAll(limit = 50).map { it.id }.toSet()
        val all = popupSearchRepository.findAll().map { it.toDomain() }

        val openedToday = all.filter { it.startDate == today && it.isNotEnded() }
        val notYetOpened = all.filter { it.startDate?.isAfter(today) == true }
        val ended = all.filter { it.endDate?.isBefore(today) == true }
        println("[노출 조건] 오늘 오픈 ${openedToday.size}건, 오픈 전 ${notYetOpened.size}건, 종료 ${ended.size}건, today=$today")

        openedToday.filterNot { it.id in seenIds }.map { it.id } shouldBe emptyList()
        notYetOpened.filter { it.id in seenIds }.map { it.id } shouldBe emptyList()
        ended.filter { it.id in seenIds }.map { it.id } shouldBe emptyList()
    }

    @Test
    fun `오픈일이 없는 팝업은 오픈일이 있는 팝업보다 뒤에 popupId 내림차순으로 온다`() {
        val seen = fetchAll(limit = 50)
        assumeTrue(seen.any { it.startDate == null }, "오픈일 없는 노출 팝업이 없음")

        val nullSection = seen.drop(seen.indexOfFirst { it.startDate == null })
        nullSection.forEach { it.startDate shouldBe null }
        nullSection.map { it.id!! } shouldBe nullSection.map { it.id!! }.sortedDescending()
    }

    @Test
    fun `DB 에 없는 popupId 가 담긴 cursor 도 값 기준으로 이어서 조회한다(삭제된 cursor 팝업)`() {
        val first = findPopups(null, Cursorable(null, 3))
        val startDate = first.content.lastOrNull()?.startDate
        assumeTrue(startDate != null, "오픈일 있는 노출 팝업이 없음")

        // 존재하지 않는 popupId: 같은 오픈일 구간의 모든 팝업보다 뒤(DESC 기준 앞)에 있던 팝업이 삭제된 상황
        val cursor = PopupSearchCursor(startDate, Long.MAX_VALUE)
        val next = findPopups(null, Cursorable(cursor, 50))
        val expected = visibleInOrder().filter { it.isAfter(cursor) }.take(50)

        printPage("삭제된 cursor=$cursor", next.content, next.hasNext)
        next.content.map { it.id } shouldBe expected.map { it.id }
    }

    @Test
    fun `오픈일 없는 구간의 cursor 는 그 구간 안에서 더 작은 popupId 부터 이어서 조회한다`() {
        val nullSection = visibleInOrder().filter { it.startDate == null }
        assumeTrue(nullSection.isNotEmpty(), "오픈일 없는 노출 팝업이 없음")

        val cursor = PopupSearchCursor(null, Long.MAX_VALUE)
        val seen = fetchAll(limit = 7, from = cursor)

        seen.map { it.id } shouldBe nullSection.map { it.id }
    }

    @Test
    fun `keyword 로 제목 · 브랜드 · 주소를 부분 일치 검색한다`() {
        val keyword =
            System.getenv("POPUP_TEST_KEYWORD")?.takeIf { it.isNotBlank() }
                ?: findPopups(null, Cursorable(null, 1))
                    .content
                    .firstOrNull()
                    ?.title
        assumeTrue(keyword != null, "검색어로 쓸 팝업이 없음")

        val slice = findPopups(keyword, Cursorable(null, 20))

        printPage("keyword='$keyword'", slice.content, slice.hasNext)
        assumeTrue(slice.content.isNotEmpty(), "'$keyword' 검색 결과 없음")

        slice.content.forEach { it.matches(keyword!!) shouldBe true }
        slice.content.forEach { it.isVisible() shouldBe true }
        slice.content shouldBe slice.content.sortedWith(latestOpenOrder)
    }

    /** 비로그인 조회. wished 는 이 테스트의 관심사가 아니라 Popup 만 꺼낸다. */
    private fun findPopups(
        keyword: String?,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<Popup> = popupListService.findPopups(null, keyword, cursorable).map { it.popup }

    private fun fetchAll(
        limit: Int,
        from: PopupSearchCursor? = null,
    ): List<Popup> {
        val seen = mutableListOf<Popup>()
        var cursor = from
        do {
            val slice = findPopups(null, Cursorable(cursor, limit))
            seen += slice.content
            cursor = slice.content.lastOrNull()?.let { PopupSearchCursor.of(it) }
        } while (slice.hasNext && cursor != null)
        return seen
    }

    private fun visibleInOrder(): List<Popup> =
        popupSearchRepository
            .findAll()
            .map { it.toDomain() }
            .filter { it.isVisible() }
            .sortedWith(latestOpenOrder)

    /** 정렬 순서상 cursor 값보다 뒤에 오는지. cursor row 존재 여부와 무관하다. */
    private fun Popup.isAfter(cursor: PopupSearchCursor): Boolean =
        latestOpenOrder.compare(Popup(source = source, title = title, startDate = cursor.startDate, id = cursor.popupId), this) < 0

    private fun Popup.isNotEnded() = endDate == null || !endDate.isBefore(today)

    private fun Popup.isOpened() = startDate == null || !startDate.isAfter(today)

    private fun Popup.isVisible() = isNotEnded() && isOpened()

    private fun Popup.matches(keyword: String): Boolean {
        val k = keyword.trim()
        return listOfNotNull(title, brand, addressRoad, addressJibun).any { it.contains(k, ignoreCase = true) }
    }

    private fun printPage(
        label: String,
        content: List<Popup>,
        hasNext: Boolean,
    ) {
        println("[$label] size=${content.size} hasNext=$hasNext")
        content.forEach { println("  id=${it.id} startDate=${it.startDate} endDate=${it.endDate} title=${it.title} brand=${it.brand}") }
    }
}
