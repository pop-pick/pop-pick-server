package com.poppick.poppick.feature.popup.domain

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType

/**
 * 지도 화면 영역(bounding box). 남서(sw) · 북동(ne) 모서리 좌표이며 경계를 포함한다.
 * 위도 -90~90, 경도 -180~180, sw ≤ ne 이어야 한다(국내 서비스라 경도 180 경계는 다루지 않는다).
 */
data class MapBounds(
    val swLat: Double,
    val swLng: Double,
    val neLat: Double,
    val neLng: Double,
) {
    init {
        requireValid(swLat.isFinite() && swLng.isFinite() && neLat.isFinite() && neLng.isFinite()) { "좌표가 숫자가 아니다" }
        requireValid(swLat in LAT_RANGE && neLat in LAT_RANGE) { "위도는 -90~90 이어야 한다: swLat=$swLat, neLat=$neLat" }
        requireValid(swLng in LNG_RANGE && neLng in LNG_RANGE) { "경도는 -180~180 이어야 한다: swLng=$swLng, neLng=$neLng" }
        requireValid(swLat <= neLat) { "swLat 는 neLat 이하여야 한다: swLat=$swLat, neLat=$neLat" }
        requireValid(swLng <= neLng) { "swLng 는 neLng 이하여야 한다: swLng=$swLng, neLng=$neLng" }
    }

    companion object {
        private val LAT_RANGE = -90.0..90.0
        private val LNG_RANGE = -180.0..180.0

        /** 요청 파라미터로 만든다. 네 좌표는 모두 필수이며, 하나라도 없거나 값이 잘못되면 INVALID_REQUEST(400). */
        fun of(
            swLat: Double?,
            swLng: Double?,
            neLat: Double?,
            neLng: Double?,
        ): MapBounds {
            if (swLat == null || swLng == null || neLat == null || neLng == null) {
                throw AppException(ErrorType.INVALID_REQUEST, "swLat · swLng · neLat · neLng 는 모두 필수다")
            }
            return MapBounds(swLat, swLng, neLat, neLng)
        }

        private inline fun requireValid(
            condition: Boolean,
            message: () -> String,
        ) {
            if (!condition) throw AppException(ErrorType.INVALID_REQUEST, message())
        }
    }
}
