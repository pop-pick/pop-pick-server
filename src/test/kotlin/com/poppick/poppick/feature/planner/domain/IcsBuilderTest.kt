package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.planner.PlannerFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

class IcsBuilderTest :
    FunSpec({
        val now = Instant.parse("2026-09-28T10:40:00Z")
        val planner = PlannerFixtures.planner()

        fun build(target: Planner = planner) = IcsBuilder.build(target, "성수", now)

        /** RFC 5545 unfolding: CRLF + 공백 한 칸을 지운다. */
        fun unfold(ics: String) = ics.replace("\r\n ", "")

        fun property(
            ics: String,
            name: String,
        ) = unfold(ics).split("\r\n").first { it.startsWith("$name:") || it.startsWith("$name;") }

        test("줄바꿈은 전부 CRLF 이고 문서도 CRLF 로 끝난다") {
            val ics = build()

            ics.replace("\r\n", "") shouldNotContain "\n"
            ics.replace("\r\n", "") shouldNotContain "\r"
            ics shouldEndWith "END:VCALENDAR\r\n"
        }

        test("필수 필드 · 순서 · TZID=Asia/Seoul · DTSTAMP(UTC)") {
            val lines = unfold(build()).split("\r\n").filter { it.isNotEmpty() }

            lines.map { it.substringBefore(':').substringBefore(';') } shouldBe
                listOf(
                    "BEGIN",
                    "VERSION",
                    "PRODID",
                    "CALSCALE",
                    "METHOD",
                    "BEGIN",
                    "UID",
                    "DTSTAMP",
                    "DTSTART",
                    "DTEND",
                    "SUMMARY",
                    "LOCATION",
                    "DESCRIPTION",
                    "END",
                    "END",
                )
            lines shouldContainInOrder
                listOf(
                    "BEGIN:VCALENDAR",
                    "VERSION:2.0",
                    "PRODID:-//POP PICK//Planner//KO",
                    "CALSCALE:GREGORIAN",
                    "METHOD:PUBLISH",
                    "BEGIN:VEVENT",
                    "UID:planner-12@poppick",
                    "DTSTAMP:20260928T104000Z",
                    "DTSTART;TZID=Asia/Seoul:20261003T140000",
                    "DTEND;TZID=Asia/Seoul:20261003T172000",
                    "SUMMARY:성수 코스",
                    "LOCATION:서울 성동구 성수동2가 301-13",
                    "END:VEVENT",
                    "END:VCALENDAR",
                )
            build() shouldNotContain "VTIMEZONE"
        }

        test("SUMMARY · LOCATION · DESCRIPTION 의 \\ ; , 줄바꿈을 이스케이프한다") {
            val tricky = planner.copy(title = "성수; 뷰티, 체험\\코스", summary = "첫 줄\n둘째 줄")

            val ics = build(tricky)

            property(ics, "SUMMARY") shouldBe "SUMMARY:성수\\; 뷰티\\, 체험\\\\코스"
            property(ics, "DESCRIPTION") shouldContain "DESCRIPTION:첫 줄\\n둘째 줄\\n\\n1. 14:00 팝업 1 (60분)"
        }

        test("DESCRIPTION 은 방문 순서대로, 마지막 줄은 출처 한 줄") {
            val description = property(build(), "DESCRIPTION")

            description.split("\\n").filter { Regex("^\\d\\. ").containsMatchIn(it) } shouldBe
                listOf("1. 14:00 팝업 1 (60분)", "2. 15:10 팝업 2 (60분)", "3. 16:20 팝업 3 (60분)")
            description shouldContain "→ 도보 10분"
            description shouldEndWith "\\n\\nPOP PICK 에서 만든 코스"
        }

        test("75옥텟 넘는 한글 줄은 UTF-8 경계에서 접히고, 펼치면 원문과 같다") {
            val longTitle = "성수동 라이프스타일과 뷰티 체험을 한 번에 즐기는 아주 긴 팝업 코스 제목입니다 가나다라마바사"
            val ics = build(planner.copy(title = longTitle))
            val strict = UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)

            ics.split("\r\n").forEach { line ->
                val bytes = line.toByteArray(UTF_8)
                bytes.size shouldBeLessThanOrEqual 75
                // 줄 단위로 디코딩해도 깨지지 않는다 = 문자 중간에서 끊지 않았다.
                strict.decode(ByteBuffer.wrap(bytes)).toString() shouldBe line
            }
            // SUMMARY(한글 60자 ≈ 170옥텟)가 실제로 접혔다.
            ics.split("\r\n").count { it.startsWith(" ") } shouldBeGreaterThan 0
            property(ics, "SUMMARY") shouldBe "SUMMARY:$longTitle"
        }
    })
