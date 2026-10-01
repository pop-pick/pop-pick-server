package com.poppick.poppick.feature.popup.domain

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MapBoundsTest :
    FunSpec({
        fun invalid(block: () -> Unit) = shouldThrow<AppException>(block).errorType shouldBe ErrorType.INVALID_REQUEST

        test("정상 영역을 만든다") {
            MapBounds.of(37.5, 126.95, 37.6, 127.1) shouldBe MapBounds(37.5, 126.95, 37.6, 127.1)
        }

        test("경계값(±90, ±180)과 한 점짜리 영역(sw == ne)은 허용한다") {
            MapBounds.of(-90.0, -180.0, 90.0, 180.0)
            MapBounds.of(37.5, 127.0, 37.5, 127.0)
        }

        test("네 좌표 중 하나라도 없으면 INVALID_REQUEST") {
            invalid { MapBounds.of(null, 126.95, 37.6, 127.1) }
            invalid { MapBounds.of(37.5, null, 37.6, 127.1) }
            invalid { MapBounds.of(37.5, 126.95, null, 127.1) }
            invalid { MapBounds.of(37.5, 126.95, 37.6, null) }
            invalid { MapBounds.of(null, null, null, null) }
        }

        test("위도가 -90~90 밖이면 INVALID_REQUEST") {
            invalid { MapBounds.of(-90.1, 126.95, 37.6, 127.1) }
            invalid { MapBounds.of(37.5, 126.95, 90.1, 127.1) }
        }

        test("경도가 -180~180 밖이면 INVALID_REQUEST") {
            invalid { MapBounds.of(37.5, -180.1, 37.6, 127.1) }
            invalid { MapBounds.of(37.5, 126.95, 37.6, 180.1) }
        }

        test("swLat > neLat 이면 INVALID_REQUEST") {
            invalid { MapBounds.of(37.6, 126.95, 37.5, 127.1) }
        }

        test("swLng > neLng 이면 INVALID_REQUEST") {
            invalid { MapBounds.of(37.5, 127.1, 37.6, 126.95) }
        }

        test("NaN · Infinity 는 INVALID_REQUEST") {
            invalid { MapBounds.of(Double.NaN, 126.95, 37.6, 127.1) }
            invalid { MapBounds.of(37.5, 126.95, 37.6, Double.POSITIVE_INFINITY) }
        }
    })
