package com.poppick.poppick.feature.popuplist.business

import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.member.implement.MemberPreferenceReader
import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popuplist.domain.PopupListItem
import com.poppick.poppick.feature.popuplist.implement.PopupListReader
import com.poppick.poppick.feature.wish.implement.WishReader
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.util.KST
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.LocalDate

private val log = KotlinLogging.logger { }

@Service
class PopupListService(
    private val popupListReader: PopupListReader,
    private val interestCategoryReader: InterestCategoryReader,
    private val favoriteAreaReader: FavoriteAreaReader,
    private val memberPreferenceReader: MemberPreferenceReader,
    private val wishReader: WishReader,
) {
    companion object {
        /** 홈 "지금 인기 있는 팝업" 개수. */
        const val POPULAR_POPUP_COUNT = 3

        /** 홈 회원 추천 팝업 개수. */
        const val RECOMMENDED_POPUP_COUNT = 3

        /**
         * 지도 한 번에 내려주는 최대 마커 수(안전 상한). 넓은 영역 요청 · 데이터 증가 시 응답 크기와 지도 렌더링 부담을 막는다.
         * 현재 전체 팝업이 100건 미만이라 서울 전역을 요청해도 걸리지 않는다. 걸리면 WARN 을 남기고 인기 팝업부터 남긴다.
         */
        const val MAP_POPUP_LIMIT = 500
    }

    /**
     * keyword 는 이름 · 브랜드 · 주소에 더해 상권 이름(findKeywordAreaIds)으로도 찾는다. areaId 가 있으면 그 상권 팝업만(keyword 와 AND).
     * memberKey 가 null(비로그인)이면 찜을 조회하지 않고 전부 wished = false.
     */
    fun findPopups(
        memberKey: String?,
        keyword: String?,
        areaId: Int?,
        sort: PopupSortType,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<PopupListItem> {
        val slice = popupListReader.findPopups(keyword, findKeywordAreaIds(keyword), areaId, LocalDate.now(KST), sort, cursorable)
        val wishedIds = findWishedIds(memberKey, slice.content)
        return slice.map { PopupListItem(it, it.id in wishedIds) }
    }

    /** 지금 인기 있는 팝업(최대 POPULAR_POPUP_COUNT 개)에 찜 여부를 붙인다. memberKey 가 null 이면 전부 wished = false. */
    fun findPopularPopups(memberKey: String?): List<PopupListItem> = withWished(memberKey, popularPopups())

    /**
     * 회원 추천 팝업(최대 RECOMMENDED_POPUP_COUNT 개). 관심 카테고리 또는 선호 지역이 맞는 노출 중인 팝업을 인기순으로 뽑고,
     * 모자라면 인기 팝업(popularPopups)에서 이미 뽑은 팝업을 빼고 채운다. 선호값이 없으면 인기 팝업 그대로다.
     * 일치 팝업이 k(< 3)개면 인기 Top3 에서 많아야 k개가 겹치므로 Top3 만으로 3 - k 개를 채울 수 있다. 조회수는 올리지 않는다.
     */
    fun findRecommendedPopups(memberKey: String): List<PopupListItem> {
        val preference = memberPreferenceReader.find(memberKey)
        val preferred =
            if (preference.interestCategoryIds.isEmpty() && preference.favoriteAreaIds.isEmpty()) {
                emptyList()
            } else {
                popupListReader.findPreferredPopups(
                    preference.interestCategoryIds,
                    preference.favoriteAreaIds,
                    LocalDate.now(KST),
                    RECOMMENDED_POPUP_COUNT,
                )
            }
        if (preferred.size >= RECOMMENDED_POPUP_COUNT) return withWished(memberKey, preferred)

        val preferredIds = preferred.map { it.id }.toSet()
        val fill = popularPopups().filterNot { it.id in preferredIds }
        return withWished(memberKey, preferred + fill.take(RECOMMENDED_POPUP_COUNT - preferred.size))
    }

    /**
     * 지도 영역 안의 노출 중인 팝업(최대 MAP_POPUP_LIMIT 개). 노출 조건 · keyword 조건(상권 이름 포함)은 목록과 같다. 조회수는 올리지 않는다.
     * 상한 초과 여부를 알기 위해 한 건 더 조회하고, 초과하면 WARN 후 상한만큼 돌려준다.
     */
    fun findMapPopups(
        keyword: String?,
        bounds: MapBounds,
    ): List<Popup> {
        val popups = popupListReader.findMapPopups(keyword, findKeywordAreaIds(keyword), LocalDate.now(KST), bounds, MAP_POPUP_LIMIT + 1)
        if (popups.size <= MAP_POPUP_LIMIT) return popups

        log.warn { "map: 지도 마커 상한($MAP_POPUP_LIMIT) 초과, 인기순 상위만 반환 bounds=$bounds keyword=$keyword" }
        return popups.take(MAP_POPUP_LIMIT)
    }

    /** 카테고리 id → 이름. 카드 뱃지 표시용이며, 요청마다 한 번만 조회해 모든 카드에 쓴다. */
    fun findCategoryNames(): Map<Int, String> = interestCategoryReader.findAll().associate { it.id to it.category }

    /** 상권 id → 이름. 목록 · 지도 카드 지역 뱃지용이며, 요청마다 한 번만 조회해 모든 카드에 쓴다. */
    fun findAreaNames(): Map<Int, String> = favoriteAreaReader.findAll().associate { it.id to it.area }

    /**
     * 지금 인기 있는 팝업(최대 POPULAR_POPUP_COUNT 개). 목록 인기순(sort=popular)의 첫 페이지와 같은 조회다:
     * 노출 조건이 같고 view_count DESC, popup_id DESC. 조회수는 올리지 않는다. 추천 채우기에서도 쓴다.
     */
    private fun popularPopups(): List<Popup> =
        popupListReader
            .findPopups(null, emptyList(), null, LocalDate.now(KST), PopupSortType.POPULAR, Cursorable(null, POPULAR_POPUP_COUNT))
            .content

    /** 찜 여부를 붙인다. memberKey 가 null(비로그인)이면 찜을 조회하지 않고 전부 wished = false. */
    private fun withWished(
        memberKey: String?,
        popups: List<Popup>,
    ): List<PopupListItem> {
        val wishedIds = findWishedIds(memberKey, popups)
        return popups.map { PopupListItem(it, it.id in wishedIds) }
    }

    private fun findWishedIds(
        memberKey: String?,
        popups: List<Popup>,
    ): Set<Long> = memberKey?.let { wishReader.findWishedPopupIds(it, popups.mapNotNull { popup -> popup.id }) }.orEmpty()

    /**
     * 이름이 keyword 와 부분 일치(대소문자 무시)하는 상권 id. 예: "홍" · "홍대" → 홍대.
     * 주소에 상권 이름이 없어도 area_id 로 찾게 하려는 것이며, keyword 가 없으면 상권을 조회하지 않는다.
     */
    private fun findKeywordAreaIds(keyword: String?): List<Int> {
        val trimmed = keyword?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return favoriteAreaReader.findAll().filter { it.area.contains(trimmed, ignoreCase = true) }.map { it.id }
    }
}
