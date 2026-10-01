package com.poppick.poppick.feature.wish.domain

import com.poppick.poppick.feature.popup.domain.Popup
import java.time.LocalDate

/** 내 찜 목록 항목. 종료된 팝업도 포함한다. */
data class WishedPopup(
    val wish: PopupWish,
    val popup: Popup,
    /** endDate < today(KST). 종료일 없으면 false. */
    val ended: Boolean,
) {
    companion object {
        fun of(
            wish: PopupWish,
            popup: Popup,
            today: LocalDate,
        ) = WishedPopup(wish, popup, popup.endDate?.isBefore(today) == true)
    }
}
