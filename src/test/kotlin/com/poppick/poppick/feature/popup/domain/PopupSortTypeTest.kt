package com.poppick.poppick.feature.popup.domain

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PopupSortTypeTest :
    FunSpec({
        test("latest · popular 를 대소문자 · 앞뒤 공백 무시하고 변환한다") {
            PopupSortType.from("latest") shouldBe PopupSortType.LATEST
            PopupSortType.from("popular") shouldBe PopupSortType.POPULAR
            PopupSortType.from(" POPULAR ") shouldBe PopupSortType.POPULAR
            PopupSortType.from("Latest") shouldBe PopupSortType.LATEST
        }

        test("생략 · 공백이면 기본값 LATEST(기존 최신순 유지)") {
            PopupSortType.DEFAULT shouldBe PopupSortType.LATEST
            PopupSortType.from(null) shouldBe PopupSortType.LATEST
            PopupSortType.from("") shouldBe PopupSortType.LATEST
            PopupSortType.from("  ") shouldBe PopupSortType.LATEST
        }

        test("알 수 없는 값은 INVALID_PAGING_PARAMETER(400)") {
            listOf("views", "LATEST_", "popularity", "1").forEach {
                shouldThrow<AppException> { PopupSortType.from(it) }.errorType shouldBe ErrorType.INVALID_PAGING_PARAMETER
            }
        }

        test("요청 값은 소문자 latest · popular") {
            PopupSortType.entries.map { it.value } shouldBe listOf("latest", "popular")
        }
    })
