package com.poppick.poppick.feature.popup.domain

/** WGS84 좌표. */
data class GeoPoint(
    val lat: Double,
    val lng: Double,
)

/** 도보 경로 한 구간(연속한 두 지점 사이). */
data class RouteLeg(
    val distanceM: Int,
    val timeSec: Int,
    /** 지도에 그릴 경로(최대 PathSampler.DEFAULT_MAX 점). 이동이 없는 구간은 빈 리스트. */
    val path: List<GeoPoint>,
)

/** 도보 길찾기 결과. legs 는 요청 지점 순서대로 (지점 수 - 1) 개. */
data class WalkRoute(
    val totalDistanceM: Int,
    val totalTimeSec: Int,
    val legs: List<RouteLeg>,
)
