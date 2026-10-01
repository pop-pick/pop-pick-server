package com.poppick.poppick.feature.popup.domain

import kotlin.math.roundToInt

/**
 * 경로 좌표 축소. 연속 중복점을 지우고, max 를 넘으면 첫 점 · 끝 점을 포함해 균등 간격으로 max 개만 남긴다.
 * 앱이 지도에 그리는 용도라 100점이면 충분하다(도보 1km 경로가 수백 점 나오는 걸 막는다).
 */
object PathSampler {
    const val DEFAULT_MAX = 100

    fun sample(
        points: List<GeoPoint>,
        max: Int = DEFAULT_MAX,
    ): List<GeoPoint> {
        require(max >= 2) { "max 는 2 이상" }
        val deduped = points.filterIndexed { index, point -> index == 0 || point != points[index - 1] }
        if (deduped.size <= max) return deduped

        val step = (deduped.size - 1).toDouble() / (max - 1)
        return (0 until max).map { deduped[(it * step).roundToInt()] }
    }
}
