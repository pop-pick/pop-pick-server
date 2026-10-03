package com.poppick.poppick.feature.popup.domain

/** 출처 페이지에서 읽은 대표 이미지 · 제목. imageUrl 은 https 절대 URL 로 정규화된 값. */
data class PageMeta(
    val imageUrl: String?,
    val title: String?,
)
