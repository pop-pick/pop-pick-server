package com.poppick.poppick.feature.wish.domain

import java.time.OffsetDateTime

/** 회원의 팝업 찜. (memberKey, popupId) 로 유일. */
data class PopupWish(
    val id: Long,
    val memberKey: String,
    val popupId: Long,
    val createdAt: OffsetDateTime,
)
