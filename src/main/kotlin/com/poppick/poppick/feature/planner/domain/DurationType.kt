package com.poppick.poppick.feature.planner.domain

/**
 * 코스 소요시간 유형. 방문 개수 · 예산은 도메인 규칙이라 설정으로 빼지 않는다.
 * maxStops 5 는 카카오 길찾기 경유지 한도(출발 · 도착 + 경유지) 때문에 넘기지 않는다.
 */
enum class DurationType(
    /** 프롬프트에 그대로 들어가는 이름. */
    val label: String,
    val minStops: Int,
    val maxStops: Int,
    /** 총 소요 예산(분). 체류 + 이동. 체류 합계는 이 값의 70% 안에서 잡는다. */
    val budgetMin: Int,
) {
    SHORT(label = "짧게", minStops = 2, maxStops = 2, budgetMin = 180),
    HALF_DAY(label = "반나절", minStops = 3, maxStops = 5, budgetMin = 300),
}
