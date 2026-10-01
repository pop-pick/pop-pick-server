package com.poppick.poppick.feature.popup.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PathSamplerTest :
    FunSpec({
        fun line(size: Int) = (0 until size).map { GeoPoint(lat = 37.5 + it * 1e-5, lng = 127.0) }

        test("100점 이하는 그대로") {
            val points = line(100)

            PathSampler.sample(points) shouldBe points
        }

        test("500점은 첫 점 · 끝 점을 포함해 100점으로 균등 샘플링") {
            val points = line(500)

            val sampled = PathSampler.sample(points)

            sampled.size shouldBe 100
            sampled.first() shouldBe points.first()
            sampled.last() shouldBe points.last()
            sampled.distinct().size shouldBe 100
            sampled shouldBe sampled.sortedBy { it.lat }
        }

        test("연속 중복점은 지운다(떨어진 중복은 둔다)") {
            val a = GeoPoint(37.5, 127.0)
            val b = GeoPoint(37.6, 127.1)

            PathSampler.sample(listOf(a, a, b, b, b, a)) shouldBe listOf(a, b, a)
        }
    })
