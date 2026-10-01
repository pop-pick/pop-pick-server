package com.poppick.poppick.feature.popuplist

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupSearchRepository
import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
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
        val slice = findPopups(null, null, PopupSortType.LATEST, Cursorable(null, 20))

        printPage("keyword 없음, limit=20", slice.content, slice.hasNext)
        assumeTrue(slice.content.isNotEmpty(), "노출 중인 팝업이 없음")

        slice.content.size shouldBeLessThanOrEqual 20
        slice.content shouldBe slice.content.sortedWith(latestOpenOrder)
        slice.content.forEach { it.isVisible() shouldBe true }
    }

    @Test
    fun `limit 만큼만 반환하고 남은 데이터가 있으면 hasNext 가 true 다`() {
        val all = findPopups(null, null, PopupSortType.LATEST, Cursorable(null, 50))
        val slice = findPopups(null, null, PopupSortType.LATEST, Cursorable(null, 3))

        printPage("limit=3", slice.content, slice.hasNext)

        slice.content.size shouldBe minOf(3, all.content.size)
        slice.hasNext shouldBe (all.content.size > 3)
    }

    @Test
    fun `첫 페이지 마지막 팝업을 cursor 로 넘기면 정렬 순서상 그 다음부터 이어서 조회한다`() {
        val first = findPopups(null, null, PopupSortType.LATEST, Cursorable(null, 3))
        assumeTrue(first.hasNext, "다음 페이지가 없음(노출 중 팝업 3개 이하)")

        val cursor = PopupSearchCursor.Latest.of(first.content.last())
        val second = findPopups(null, null, PopupSortType.LATEST, Cursorable(cursor, 3))

        printPage("1페이지 limit=3", first.content, first.hasNext)
        printPage("2페이지 cursor=$cursor limit=3", second.content, second.hasNext)

        second.content.forEach { latestOpenOrder.compare(first.content.last(), it) shouldBeLessThan 0 }
        (first.content.map { it.id } intersect second.content.map { it.id }.toSet()) shouldBe emptySet()

        // 두 페이지를 이어 붙인 결과는 limit=6 으로 한 번에 조회한 결과와 같아야 한다
        val combined = findPopups(null, null, PopupSortType.LATEST, Cursorable(null, 6))
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
        val first = findPopups(null, null, PopupSortType.LATEST, Cursorable(null, 3))
        val startDate = first.content.lastOrNull()?.startDate
        assumeTrue(startDate != null, "오픈일 있는 노출 팝업이 없음")

        // 존재하지 않는 popupId: 같은 오픈일 구간의 모든 팝업보다 뒤(DESC 기준 앞)에 있던 팝업이 삭제된 상황
        val cursor = PopupSearchCursor.Latest(startDate, Long.MAX_VALUE)
        val next = findPopups(null, null, PopupSortType.LATEST, Cursorable(cursor, 50))
        val expected = visibleInOrder().filter { it.isAfter(cursor) }.take(50)

        printPage("삭제된 cursor=$cursor", next.content, next.hasNext)
        next.content.map { it.id } shouldBe expected.map { it.id }
    }

    @Test
    fun `오픈일 없는 구간의 cursor 는 그 구간 안에서 더 작은 popupId 부터 이어서 조회한다`() {
        val nullSection = visibleInOrder().filter { it.startDate == null }
        assumeTrue(nullSection.isNotEmpty(), "오픈일 없는 노출 팝업이 없음")

        val cursor = PopupSearchCursor.Latest(null, Long.MAX_VALUE)
        val seen = fetchAll(limit = 7, from = cursor)

        seen.map { it.id } shouldBe nullSection.map { it.id }
    }

    @Test
    fun `keyword 로 제목 · 브랜드 · 주소를 부분 일치 검색한다`() {
        val keyword =
            System.getenv("POPUP_TEST_KEYWORD")?.takeIf { it.isNotBlank() }
                ?: findPopups(null, null, PopupSortType.LATEST, Cursorable(null, 1))
                    .content
                    .firstOrNull()
                    ?.title
        assumeTrue(keyword != null, "검색어로 쓸 팝업이 없음")

        val slice = findPopups(keyword, null, PopupSortType.LATEST, Cursorable(null, 20))

        printPage("keyword='$keyword'", slice.content, slice.hasNext)
        assumeTrue(slice.content.isNotEmpty(), "'$keyword' 검색 결과 없음")

        slice.content.forEach { it.matches(keyword!!) shouldBe true }
        slice.content.forEach { it.isVisible() shouldBe true }
        slice.content shouldBe slice.content.sortedWith(latestOpenOrder)
    }

    /** 인기순. 조회수 내림차순, 같은 조회수는 popupId 내림차순. */
    private val popularOrder: Comparator<Popup> =
        compareByDescending<Popup> { it.viewCount }.thenByDescending { it.id }

    @Test
    fun `인기순은 노출 대상 팝업을 조회수 내림차순 · 같은 조회수는 popupId 내림차순으로 반환한다`() {
        val slice = findPopups(null, null, PopupSortType.POPULAR, Cursorable(null, 20))

        printPage("sort=popular, limit=20", slice.content, slice.hasNext)
        assumeTrue(slice.content.isNotEmpty(), "노출 중인 팝업이 없음")

        slice.content.size shouldBeLessThanOrEqual 20
        slice.content shouldBe slice.content.sortedWith(popularOrder)
        slice.content.forEach { it.isVisible() shouldBe true }
    }

    @Test
    fun `인기순 cursor 로 끝까지 조회하면 노출 대상 전체를 누락 · 중복 없이 인기순으로 반환한다`() {
        val seen = mutableListOf<Popup>()
        var cursor: PopupSearchCursor.Popular? = null
        do {
            val slice = findPopups(null, null, PopupSortType.POPULAR, Cursorable(cursor, 7))
            seen += slice.content
            cursor = slice.content.lastOrNull()?.let { PopupSearchCursor.Popular.of(it) }
        } while (slice.hasNext && cursor != null)
        val expected =
            popupSearchRepository
                .findAll()
                .map { it.toDomain() }
                .filter { it.isVisible() }
                .sortedWith(popularOrder)

        println("[인기순 전체 순회] 노출 팝업 ${seen.size}건, 조회수 분포 ${seen.groupingBy { it.viewCount }.eachCount()}")
        seen.map { it.id } shouldBe expected.map { it.id }
    }

    @Test
    fun `인기순도 limit 만큼만 반환하고 노출 조건 · keyword 는 최신순과 같다`() {
        val latest = fetchAll(limit = 50).map { it.id }.toSet()
        val popular = findPopups(null, null, PopupSortType.POPULAR, Cursorable(null, 3))

        popular.content.size shouldBe minOf(3, latest.size)
        popular.hasNext shouldBe (latest.size > 3)

        val keyword = popular.content.firstOrNull()?.title
        assumeTrue(keyword != null, "검색어로 쓸 팝업이 없음")
        val latestHits = findPopups(keyword, null, PopupSortType.LATEST, Cursorable(null, 50)).content
        val popularHits = findPopups(keyword, null, PopupSortType.POPULAR, Cursorable(null, 50)).content

        printPage("keyword='$keyword' sort=popular", popularHits, false)
        popularHits.map { it.id }.toSet() shouldBe latestHits.map { it.id }.toSet()
        popularHits shouldBe popularHits.sortedWith(popularOrder)
        popularHits.forEach { it.matches(keyword!!) shouldBe true }
    }

    @Test
    fun `지금 인기 있는 팝업은 인기순 목록 첫 3개와 같고 노출 대상만 담는다`() {
        val top3 = popupListService.findPopularPopups(null).map { it.popup }
        val firstPage = findPopups(null, null, PopupSortType.POPULAR, Cursorable(null, 3)).content

        printPage("지금 인기 있는 팝업", top3, false)
        top3.size shouldBeLessThanOrEqual 3
        top3.map { it.id } shouldBe firstPage.map { it.id }
        top3 shouldBe top3.sortedWith(popularOrder)
        top3.forEach { it.isVisible() shouldBe true }
    }

    @Test
    fun `카테고리 이름은 interest_category 전체이고 노출 팝업의 카테고리 id 는 모두 이름을 찾을 수 있다`() {
        val names = popupListService.findCategoryNames()
        val categoryIds = fetchAll(limit = 50).mapNotNull { it.interestCategoryId }.toSet()

        println("[카테고리 이름] $names, 노출 팝업 카테고리 id=$categoryIds")
        assumeTrue(names.isNotEmpty(), "interest_category 가 비어 있음")
        categoryIds.filterNot { it in names } shouldBe emptyList()
    }

    /** 서울 전역을 넉넉히 덮는 지도 영역. */
    private val seoulBounds = MapBounds(37.40, 126.70, 37.72, 127.20)

    @Test
    fun `지도 서울 전역 조회는 좌표 있는 노출 팝업 전체를 인기순으로 돌려준다`() {
        val map = popupListService.findMapPopups(null, seoulBounds)
        val expected =
            popupSearchRepository
                .findAll()
                .map { it.toDomain() }
                .filter { it.isVisible() && it.latitude != null && it.longitude != null }
                .filter { it.latitude!! in seoulBounds.swLat..seoulBounds.neLat && it.longitude!! in seoulBounds.swLng..seoulBounds.neLng }
                .sortedWith(popularOrder)

        println("[지도 서울 전역] ${map.size}건 (상한 ${PopupListService.MAP_POPUP_LIMIT})")
        map.map { it.id } shouldBe expected.take(PopupListService.MAP_POPUP_LIMIT).map { it.id }
    }

    @Test
    fun `지도 keyword 조회는 목록 keyword 결과와 같은 팝업 집합이다(좌표 · 영역 안)`() {
        val keyword =
            popupListService.findMapPopups(null, seoulBounds).firstOrNull()?.title
        assumeTrue(keyword != null, "지도에 표시할 팝업이 없음")

        val listed = findPopups(keyword, null, PopupSortType.LATEST, Cursorable(null, 50)).content
        val mapped = popupListService.findMapPopups(keyword, seoulBounds)

        mapped.map { it.id }.toSet() shouldBe listed.filter { it.latitude != null && it.longitude != null }.map { it.id }.toSet()
    }

    @Test
    fun `지도 결과의 상권 id 는 모두 이름을 찾을 수 있다`() {
        val areaNames = popupListService.findAreaNames()
        val areaIds = popupListService.findMapPopups(null, seoulBounds).mapNotNull { it.areaId }.toSet()

        println("[상권 이름] $areaNames, 지도 팝업 상권 id=$areaIds")
        areaIds.filterNot { it in areaNames } shouldBe emptyList()
    }

    /** 비로그인 조회. wished 는 이 테스트의 관심사가 아니라 Popup 만 꺼낸다. */
    private fun findPopups(
        keyword: String?,
        areaId: Int?,
        sort: PopupSortType,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<Popup> = popupListService.findPopups(null, keyword, areaId, sort, cursorable).map { it.popup }

    private fun fetchAll(
        limit: Int,
        from: PopupSearchCursor.Latest? = null,
    ): List<Popup> {
        val seen = mutableListOf<Popup>()
        var cursor = from
        do {
            val slice = findPopups(null, null, PopupSortType.LATEST, Cursorable(cursor, limit))
            seen += slice.content
            cursor = slice.content.lastOrNull()?.let { PopupSearchCursor.Latest.of(it) }
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
    private fun Popup.isAfter(cursor: PopupSearchCursor.Latest): Boolean =
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
