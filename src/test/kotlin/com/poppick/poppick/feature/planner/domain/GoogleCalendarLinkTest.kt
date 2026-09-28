package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.planner.PlannerFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

class GoogleCalendarLinkTest :
    FunSpec({
        fun params(url: String) =
            URI(url).rawQuery.split("&").associate {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), UTF_8)
            }

        val planner = PlannerFixtures.planner().copy(title = "성수 뷰티 & 향수 코스")

        test("render TEMPLATE, dates 는 KST 로컬 시각 + ctz=Asia/Seoul") {
            val url = GoogleCalendarLink.build(planner, "성수")
            val params = params(url)

            url shouldStartWith "https://calendar.google.com/calendar/render?action=TEMPLATE&"
            params["dates"] shouldBe "20261003T140000/20261003T172000"
            params["ctz"] shouldBe "Asia/Seoul"
            params["location"] shouldBe "서울 성동구 성수동2가 301-13"
        }

        test("한글 · 공백 · & 는 인코딩되고 공백은 + 가 아니라 %20") {
            val url = GoogleCalendarLink.build(planner, "성수")
            val rawText = URI(url).rawQuery.split("&").first { it.startsWith("text=") }

            rawText shouldBe "text=%EC%84%B1%EC%88%98%20%EB%B7%B0%ED%8B%B0%20%26%20%ED%96%A5%EC%88%98%20%EC%BD%94%EC%8A%A4"
            url shouldNotContain "+"
            params(url)["text"] shouldBe "성수 뷰티 & 향수 코스"
        }

        test("details 는 CourseDescription 본문, 첫 방문지 주소가 없으면 location 은 지역 이름") {
            val noAddress = planner.copy(stops = planner.stops.map { it.copy(address = null) })
            val params = params(GoogleCalendarLink.build(noAddress, "성수", "https://pop-pick.app/share/abc"))

            params["details"] shouldBe CourseDescription.build(noAddress, "https://pop-pick.app/share/abc")
            params["location"] shouldBe "성수"
        }
    })
