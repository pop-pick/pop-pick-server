package com.poppick.poppick.feature.planner.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CandidateQueryTextTest :
    FunSpec({
        test("세 줄 전부") {
            CandidateQueryText.build(listOf("캐릭터/IP", "뷰티"), listOf("사진 찍기", "굿즈 구매"), "조용한 곳이 좋아요") shouldBe
                """
                카테고리: 캐릭터/IP, 뷰티
                체험 · 키워드: 사진 찍기, 굿즈 구매
                조용한 곳이 좋아요
                """.trimIndent()
        }

        test("카테고리만") {
            CandidateQueryText.build(listOf("F&B"), emptyList(), null) shouldBe "카테고리: F&B"
        }

        test("자유 입력만") {
            CandidateQueryText.build(emptyList(), emptyList(), "산리오 굿즈") shouldBe "산리오 굿즈"
        }

        test("전부 비면 NULL") {
            CandidateQueryText.build(emptyList(), emptyList(), null).shouldBeNull()
            CandidateQueryText.build(listOf(" "), listOf(""), "  \n ").shouldBeNull()
        }

        test("연속 공백 · 줄바꿈은 한 칸으로, 앞뒤 공백은 제거") {
            CandidateQueryText.build(listOf("  캐릭터/IP "), listOf("사진\n 찍기"), "  산리오   굿즈\n\n많은 곳  ") shouldBe
                """
                카테고리: 캐릭터/IP
                체험 · 키워드: 사진 찍기
                산리오 굿즈 많은 곳
                """.trimIndent()
        }

        test("자유 입력은 200자로 자른다") {
            CandidateQueryText.build(emptyList(), emptyList(), "가".repeat(250)) shouldBe "가".repeat(200)
        }
    })
