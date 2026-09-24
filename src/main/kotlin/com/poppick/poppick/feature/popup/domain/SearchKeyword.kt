package com.poppick.poppick.feature.popup.domain

data class SearchKeyword(
    /** 원천에 그대로 넘기는 완성 검색어. 조합(지역×주제)하지 않는다. */
    val keyword: String,
    /** 이 검색어를 사용할 탐색 원천. */
    val targetSource: SourceType,
    /** false 면 탐색 배치에서 제외. */
    val isActive: Boolean = true,
    /** 검색어 식별자(search_keyword_id). 저장 전엔 NULL. */
    val id: Long? = null,
)
