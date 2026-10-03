package com.poppick.poppick.feature.popup.domain

data class PerplexityImageResult(
    /** 모델이 고른 대표 이미지 URL. 맞는 이미지가 없으면 NULL. 확인 전 값이라 그대로 저장하지 않는다. */
    val imageUrl: String?,
    /** 모든 image_search_results 의 image_url(등장 순, 중복 제거). imageUrl 이 여기 있어야 채택한다. */
    val candidates: List<String>,
    val usage: PerplexityUsage = PerplexityUsage(),
)
