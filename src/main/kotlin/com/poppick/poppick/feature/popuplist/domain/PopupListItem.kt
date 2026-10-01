package com.poppick.poppick.feature.popuplist.domain

import com.poppick.poppick.feature.popup.domain.Popup

/** 팝업 목록 항목. wished 는 로그인 회원의 찜 여부(비로그인은 false). */
data class PopupListItem(
    val popup: Popup,
    val wished: Boolean,
)
