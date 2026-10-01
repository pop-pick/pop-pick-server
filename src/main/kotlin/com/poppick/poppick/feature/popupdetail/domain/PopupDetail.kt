package com.poppick.poppick.feature.popupdetail.domain

import com.poppick.poppick.feature.popup.domain.Popup

/** 팝업 상세. wished 는 로그인 회원의 찜 여부(비로그인은 false). */
data class PopupDetail(
    val popup: Popup,
    val wished: Boolean,
)
