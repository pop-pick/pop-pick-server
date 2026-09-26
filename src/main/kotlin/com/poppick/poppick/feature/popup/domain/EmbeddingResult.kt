package com.poppick.poppick.feature.popup.domain

/** 임베딩 요청 1회 결과. vectors 는 입력 순서와 같다. */
data class EmbeddingResult(
    val vectors: List<FloatArray>,
    val promptTokens: Int,
    /** USD */
    val costUsd: Double,
)
