package com.poppick.poppick.feature.popup.domain

data class PerplexityEnrichResult(
    val enrichment: PopupEnrichment,
    /** 모델이 참고한 검색 결과 URL(등장 순, 중복 제거). */
    val searchResultUrls: List<String>,
    val usage: PerplexityUsage = PerplexityUsage(),
)
