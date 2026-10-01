package com.poppick.poppick.feature.popup.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 두 좌표 사이 대원 거리(haversine). */
object GeoDistance {
    /** 지구 평균 반지름(m). */
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun meters(
        a: GeoPoint,
        b: GeoPoint,
    ): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }
}
