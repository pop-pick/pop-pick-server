package com.poppick.poppick.feature.popup.domain

import java.time.LocalDate

/**
 * 팝업 목록 cursor. 정렬 기준 값인 이전 페이지 마지막 팝업의 값이며, 정렬마다 구성이 다르다.
 * 조회 조건을 이 값만으로 만들기 때문에 해당 팝업 row 가 이후 삭제돼도 다음 위치를 계산할 수 있다.
 */
sealed interface PopupSearchCursor {
    /** 이 cursor 가 속한 정렬. 요청 정렬과 다르면 쓸 수 없다. */
    val sort: PopupSortType

    /** 마지막 팝업의 popup_id. */
    val popupId: Long

    /** 오픈일 최신순(start_date DESC NULLS LAST, popup_id DESC) cursor. */
    data class Latest(
        /** 마지막 팝업의 운영 시작일. NULL 이면 오픈일 없는 구간(목록 맨 뒤). */
        val startDate: LocalDate?,
        override val popupId: Long,
    ) : PopupSearchCursor {
        override val sort get() = PopupSortType.LATEST

        companion object {
            fun of(popup: Popup) = Latest(popup.startDate, popup.id!!)
        }
    }

    /** 인기순(view_count DESC, popup_id DESC) cursor. */
    data class Popular(
        /** 마지막 팝업의 조회수. */
        val viewCount: Long,
        override val popupId: Long,
    ) : PopupSearchCursor {
        override val sort get() = PopupSortType.POPULAR

        companion object {
            fun of(popup: Popup) = Popular(popup.viewCount, popup.id!!)
        }
    }

    companion object {
        fun of(
            popup: Popup,
            sort: PopupSortType,
        ): PopupSearchCursor =
            when (sort) {
                PopupSortType.LATEST -> Latest.of(popup)
                PopupSortType.POPULAR -> Popular.of(popup)
            }
    }
}
