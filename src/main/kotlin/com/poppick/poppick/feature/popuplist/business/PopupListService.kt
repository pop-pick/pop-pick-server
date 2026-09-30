package com.poppick.poppick.feature.popuplist.business

import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popuplist.implement.PopupListReader
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.util.KST
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PopupListService(
    private val popupListReader: PopupListReader,
    private val interestCategoryReader: InterestCategoryReader,
) {
    companion object {
        /** 홈 "지금 인기 있는 팝업" 개수. */
        const val POPULAR_POPUP_COUNT = 3
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

    /** 카테고리 id → 이름. 카드 뱃지 표시용이며, 요청마다 한 번만 조회해 모든 카드에 쓴다. */
    fun findCategoryNames(): Map<Int, String> = interestCategoryReader.findAll().associate { it.id to it.category }
}
