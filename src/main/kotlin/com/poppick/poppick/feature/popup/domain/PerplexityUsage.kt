package com.poppick.poppick.feature.popup.domain

/** Perplexity 요청 1건의 사용량. 응답에 없으면 비용 · 검색 횟수는 0, 토큰은 NULL. */
data class PerplexityUsage(
    /** 요청 비용(USD). */
    val costUsd: Double = 0.0,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    /** 웹 검색 호출 횟수. */
    val searchCalls: Int = 0,
)
