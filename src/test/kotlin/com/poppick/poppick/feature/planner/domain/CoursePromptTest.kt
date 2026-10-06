package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.LocalDate
import java.time.LocalTime

class CoursePromptTest :
    FunSpec({
        val condition =
            CourseCondition(
                visitDate = LocalDate.of(2026, 10, 3),
                startTime = LocalTime.of(14, 0),
                accompanyType = AccompanyType.WITH_FRIEND,
                durationType = DurationType.HALF_DAY,
                note = "  향수 만들기   체험이\n있으면 좋겠어요 ",
            )
        val categories = mapOf(1 to "캐릭터/IP", 5 to "뷰티")

        val full =
            Popup(
                id = 1722,
                source = SourceType.KAKAO_MAP,
                title = "진격의 거인展 FINAL",
                brand = "진격의 거인",
                interestCategoryId = 1,
                description = "소개 문장.",
                tags = listOf("포토존", " 굿즈 "),
                startDate = LocalDate.of(2026, 6, 19),
                endDate = LocalDate.of(2026, 11, 1),
                openingHours = "매일 11:00~20:00",
                addressJibun = "서울 성동구 성수동2가 273-13",
                latitude = 37.544612,
                longitude = 127.05593,
            )
        val bare = Popup(id = 1731, source = SourceType.KAKAO_MAP, title = "이름만 있는 팝업")

        fun candidates(vararg popups: Popup) = popups.map { CandidatePopup(it, 0.3) }

        test("조건 블록: 요일 · 시작 시각 · 동행 · 개수 범위 · 예산 · 자유 입력(공백 정리)") {
            CoursePrompt.build(condition, emptyList()).substringBefore("\n\n") shouldBe
                """
                ## 조건
                - 방문일: 2026-10-03 (토)
                - 시작 시각: 14:00
                - 동행: 친구와
                - 소요시간: 반나절 → 방문 개수 3~5곳, 총 소요 예산 300분
                - 자유 입력: 향수 만들기 체험이 있으면 좋겠어요
                """.trimIndent()
        }

        test("note 가 없으면 자유 입력 줄을 뺀다 · SHORT 는 개수를 하나로") {
            val block =
                CoursePrompt
                    .build(
                        condition.copy(note = " ", durationType = DurationType.SHORT, visitDate = LocalDate.of(2026, 10, 5)),
                        emptyList(),
                    ).substringBefore("\n\n")

            block shouldNotContain "자유 입력"
            block shouldContain "- 방문일: 2026-10-05 (월)"
            block shouldContain "- 소요시간: 짧게 → 방문 개수 2곳, 총 소요 예산 180분"
        }

        test("후보 렌더링: 헤더 한 줄 + 부가 줄, 좌표는 소수 4자리") {
            CoursePrompt.build(condition, candidates(full), categories).substringAfter("\n\n") shouldBe
                """
                ## 후보 팝업 (1건)
                앞쪽일수록 관심사와 더 가깝다.
                [1722] 진격의 거인展 FINAL | 카테고리: 캐릭터/IP · 브랜드: 진격의 거인 | 성동구 성수동2가 | 37.5446,127.0559
                  소개: 소개 문장.
                  체험 · 키워드: 포토존, 굿즈
                  운영: 2026-06-19 ~ 2026-11-01 · 매일 11:00~20:00
                """.trimIndent()
        }

        test("값이 없는 줄 · 구간은 뺀다") {
            CoursePrompt.build(condition, candidates(bare), categories).substringAfter("\n\n") shouldBe
                """
                ## 후보 팝업 (1건)
                앞쪽일수록 관심사와 더 가깝다.
                [1731] 이름만 있는 팝업
                """.trimIndent()
        }

        test("종료일만 있으면 '~ 종료일', 지번이 없으면 도로명의 구만") {
            val popup = bare.copy(endDate = LocalDate.of(2026, 10, 12), addressRoad = "서울 마포구 와우산로 1")

            val rendered = CoursePrompt.build(condition, candidates(popup)).substringAfter("\n\n")

            rendered shouldContain "[1731] 이름만 있는 팝업 | 마포구"
            rendered shouldContain "  운영: ~ 2026-10-12"
        }

        test("시작일만 있으면 종료일을 '미정' 으로 쓴다") {
            val popup = bare.copy(startDate = LocalDate.of(2026, 9, 1))

            val rendered = CoursePrompt.build(condition, candidates(popup))

            rendered shouldContain "  운영: 2026-09-01 ~ 미정"
            rendered shouldNotContain "null"
        }

        test("소개는 150자에서 자른다") {
            val rendered = CoursePrompt.build(condition, candidates(full.copy(description = "가".repeat(200))))

            rendered shouldContain "  소개: ${"가".repeat(150)}\n"
        }

        test("후보 순서를 그대로 유지한다") {
            val rendered = CoursePrompt.build(condition, candidates(bare, full, bare.copy(id = 1700)))
            val ids = Regex("^\\[(\\d+)]", RegexOption.MULTILINE).findAll(rendered).map { it.groupValues[1] }.toList()

            ids shouldBe listOf("1731", "1722", "1700")
            rendered shouldContain "## 후보 팝업 (3건)"
        }
    })
