package com.poppick.poppick.feature.popup.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class GeoRectTest :
    FunSpec({
        test("rect 문자열을 파싱하고 같은 형식으로 되돌린다") {
            val rect = GeoRect.parse("126.764, 37.413,127.184,37.715")

            rect shouldBe GeoRect(126.764, 37.413, 127.184, 37.715)
            rect.toParam() shouldBe "126.764,37.413,127.184,37.715"
        }

        test("4등분은 좌하 · 우하 · 좌상 · 우상 순서이고 소수 6자리로 표기한다") {
            val quarters = GeoRect.parse("126.764,37.413,127.184,37.715").quarters()

            quarters.map { it.toParam() } shouldContainExactly
                listOf(
                    "126.764,37.413,126.974,37.564",
                    "126.974,37.413,127.184,37.564",
                    "126.764,37.564,126.974,37.715",
                    "126.974,37.564,127.184,37.715",
                )
            // 6단계 분할해도 부동소수 꼬리가 붙지 않는다
            var cell = quarters[0]
            repeat(5) { cell = cell.quarters()[3] }
            cell.toParam().split(",").all { it.substringAfter(".", "").length <= 6 } shouldBe true
        }

        test("숫자 4개가 아니거나 좌하단이 우상단보다 크면 예외") {
            shouldThrow<IllegalArgumentException> { GeoRect.parse("126.764,37.413,127.184") }
            shouldThrow<IllegalArgumentException> { GeoRect.parse("127.184,37.413,126.764,37.715") }
        }
    })
