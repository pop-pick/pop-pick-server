package com.poppick.poppick.feature.popuplist.business

import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popuplist.implement.PopupListReader
import com.poppick.poppick.global.paging.Cursorable
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
) {
    companion object {
        /** 홈 "지금 인기 있는 팝업" 개수. */
        const val POPULAR_POPUP_COUNT = 3

        /**
         * 지도 한 번에 내려주는 최대 마커 수(안전 상한). 넓은 영역 요청 · 데이터 증가 시 응답 크기와 지도 렌더링 부담을 막는다.
         * 현재 전체 팝업이 100건 미만이라 서울 전역을 요청해도 걸리지 않는다. 걸리면 WARN 을 남기고 인기 팝업부터 남긴다.
         */
        const val MAP_POPUP_LIMIT = 500
    }

    fun findPopups(
        keyword: String?,
        sort: PopupSortType,
        cursorable: Cursorable<PopupSearchCursor>,
    ) = popupListReader.findPopups(keyword, LocalDate.now(KST), sort, cursorable)

    /**
     * 지금 인기 있는 팝업(최대 POPULAR_POPUP_COUNT 개). 목록 인기순(sort=popular)의 첫 페이지와 같은 조회다:
     * 노출 조건이 같고 view_count DESC, popup_id DESC. 조회수는 올리지 않는다.
     */
    fun findPopularPopups(): List<Popup> =
        popupListReader
            .findPopups(null, LocalDate.now(KST), PopupSortType.POPULAR, Cursorable(null, POPULAR_POPUP_COUNT))
            .content

    /**
     * 지도 영역 안의 노출 중인 팝업(최대 MAP_POPUP_LIMIT 개). 노출 조건 · keyword 조건은 목록과 같다. 조회수는 올리지 않는다.
     * 상한 초과 여부를 알기 위해 한 건 더 조회하고, 초과하면 WARN 후 상한만큼 돌려준다.
     */
    fun findMapPopups(
        keyword: String?,
        bounds: MapBounds,
    ): List<Popup> {
        val popups = popupListReader.findMapPopups(keyword, LocalDate.now(KST), bounds, MAP_POPUP_LIMIT + 1)
        if (popups.size <= MAP_POPUP_LIMIT) return popups

        log.warn { "map: 지도 마커 상한($MAP_POPUP_LIMIT) 초과, 인기순 상위만 반환 bounds=$bounds keyword=$keyword" }
        return popups.take(MAP_POPUP_LIMIT)
    }

    /** 카테고리 id → 이름. 카드 뱃지 표시용이며, 요청마다 한 번만 조회해 모든 카드에 쓴다. */
    fun findCategoryNames(): Map<Int, String> = interestCategoryReader.findAll().associate { it.id to it.category }

    /** 상권 id → 이름. 지도 카드 지역 뱃지용이며, 요청마다 한 번만 조회해 모든 카드에 쓴다. */
    fun findAreaNames(): Map<Int, String> = favoriteAreaReader.findAll().associate { it.id to it.area }
}
