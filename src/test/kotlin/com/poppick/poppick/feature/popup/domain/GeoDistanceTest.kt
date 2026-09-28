package com.poppick.poppick.feature.popup.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class GeoDistanceTest :
    FunSpec({
        test("성수역 – 서울숲역 약 1.1km(오차 5%)") {
            val seongsu = GeoPoint(lat = 37.544581, lng = 127.055961)
            val seoulForest = GeoPoint(lat = 37.543617, lng = 127.044707)

            GeoDistance.meters(seongsu, seoulForest) shouldBe (1_000.0 plusOrMinus 50.0)
        }

        test("같은 점은 0") {
            val point = GeoPoint(lat = 37.5122, lng = 127.0995)

            GeoDistance.meters(point, point) shouldBe 0.0
        }
    })
