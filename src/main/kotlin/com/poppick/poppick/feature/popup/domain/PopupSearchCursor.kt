package com.poppick.poppick.feature.popup.domain

import java.time.LocalDate

/**
 * 팝업 목록 cursor. 정렬(start_date DESC NULLS LAST, popup_id DESC) 기준인 이전 페이지 마지막 팝업의 값이다.
 * 조회 조건을 이 값만으로 만들기 때문에 해당 팝업 row 가 이후 삭제돼도 다음 위치를 계산할 수 있다.
 */
data class PopupSearchCursor(
    /** 마지막 팝업의 운영 시작일. NULL 이면 오픈일 없는 구간(목록 맨 뒤). */
    val startDate: LocalDate?,
    /** 마지막 팝업의 popup_id. */
    val popupId: Long,
) {
    companion object {
        fun of(popup: Popup) = PopupSearchCursor(popup.startDate, popup.id!!)
    }
}
