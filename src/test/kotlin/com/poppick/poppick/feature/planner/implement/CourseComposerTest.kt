package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.member.domain.InterestCategory
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.planner.domain.CandidatePopup
import com.poppick.poppick.feature.planner.domain.CourseCondition
import com.poppick.poppick.feature.planner.domain.CourseGenerationException
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.dataaccess.client.openai.OpenAiChatClient
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalTime

class CourseComposerTest :
    FunSpec({
        val condition =
            CourseCondition(
                visitDate = LocalDate.of(2026, 10, 3),
                startTime = LocalTime.of(14, 0),
                accompanyType = AccompanyType.WITH_FRIEND,
                durationType = DurationType.HALF_DAY,
                note = null,
            )
        val candidates = (1L..6L).map { CandidatePopup(Popup(id = it, source = SourceType.KAKAO_MAP, title = "팝업$it"), 0.1 * it) }

        class Fixture {
            val chatClient = mockk<OpenAiChatClient>()
            val inputs = mutableListOf<String>()
            val composer =
                CourseComposer(
                    openAiChatClient = chatClient,
                    interestCategoryReader =
                        mockk<InterestCategoryReader> {
                            every { findAll() } returns
                                listOf(InterestCategory(1, "캐릭터/IP"))
                        },
                    jsonMapper = Fixtures.jsonMapper,
                )

            fun responds(vararg texts: String) {
                val queue = ArrayDeque(texts.toList())
                every { chatClient.complete(any(), capture(inputs), "planner_course", any()) } answers { queue.removeFirst() }
            }
        }

        fun stop(
            popupId: Long,
            stayMin: Int = 60,
            reason: String = "추천 이유 $popupId",
        ) = """{"popupId": $popupId, "stayMin": $stayMin, "reason": "$reason"}"""

        fun response(
            vararg stops: String,
            title: String = "성수 캐릭터 코스",
            summary: String = "캐릭터 팝업을 모아 둘러보는 코스",
        ) = """{"title": "$title", "summary": "$summary", "stops": [${stops.joinToString(",")}]}"""

        test("정상 응답: 응답 순서대로 후보 Popup 을 매핑한다") {
            val fixture = Fixture()
            fixture.responds(response(stop(3), stop(1), stop(5)))

            val draft = fixture.composer.compose(condition, candidates)

            draft.title shouldBe "성수 캐릭터 코스"
            draft.summary shouldBe "캐릭터 팝업을 모아 둘러보는 코스"
            draft.stops.map { it.popup.id } shouldBe listOf(3L, 1L, 5L)
            draft.stops.map { it.stayMin } shouldBe listOf(60, 60, 60)
            draft.stops.map { it.reason } shouldBe listOf("추천 이유 3", "추천 이유 1", "추천 이유 5")
            verify(exactly = 1) { fixture.chatClient.complete(any(), any(), any(), any()) }
        }

        test("후보에 없는 id · 중복은 버리고 나머지로 진행한다") {
            val fixture = Fixture()
            fixture.responds(response(stop(1), stop(9999), stop(2), stop(1), stop(3)))

            fixture.composer
                .compose(condition, candidates)
                .stops
                .map { it.popup.id } shouldBe listOf(1L, 2L, 3L)
            verify(exactly = 1) { fixture.chatClient.complete(any(), any(), any(), any()) }
        }

        test("개수 미달이면 사유를 붙여 1회 재시도하고 성공한다") {
            val fixture = Fixture()
            fixture.responds(response(stop(1), stop(9999)), response(stop(1), stop(2), stop(3)))

            val draft = fixture.composer.compose(condition, candidates)

            draft.stops.size shouldBe 3
            fixture.inputs.size shouldBe 2
            fixture.inputs[0] shouldNotContain "## 이전 응답의 문제"
            fixture.inputs[1] shouldBe
                fixture.inputs[0] + "\n\n## 이전 응답의 문제\n- 후보에 없는 popupId 9999\n- 방문 개수 1곳은 최소 3곳 미만"
        }

        test("2회 모두 실패하면 CourseGenerationException") {
            val fixture = Fixture()
            fixture.responds(response(stop(1)), response(stop(2), title = " "))

            val e = shouldThrow<CourseGenerationException> { fixture.composer.compose(condition, candidates) }

            e.message!! shouldContain "title 이 비었음"
            verify(exactly = 2) { fixture.chatClient.complete(any(), any(), any(), any()) }
        }

        test("JSON 파싱 실패도 재시도한다") {
            val fixture = Fixture()
            fixture.responds("{not json", response(stop(1), stop(2), stop(3)))

            fixture.composer
                .compose(condition, candidates)
                .stops.size shouldBe 3
            fixture.inputs[1] shouldContain "## 이전 응답의 문제\n- JSON 파싱 실패"
        }

        test("stayMin 은 20~120 clamp 후 10분 단위 반올림") {
            val fixture = Fixture()
            fixture.responds(response(stop(1, stayMin = 5), stop(2, stayMin = 44), stop(3, stayMin = 45)))

            fixture.composer
                .compose(condition, candidates)
                .stops
                .map { it.stayMin } shouldBe listOf(20, 40, 50)
        }

        test("체류 합계가 예산의 70% 를 넘으면 비율대로 줄인다(10분 단위, 최소 20)") {
            val fixture = Fixture()
            // 합계 120 × 3 = 360 > 210 → 비율 0.583 → 70 씩.
            fixture.responds(response(stop(1, stayMin = 150), stop(2, stayMin = 120), stop(3, stayMin = 120)))

            val stayMins =
                fixture.composer
                    .compose(condition, candidates)
                    .stops
                    .map { it.stayMin }

            stayMins shouldBe listOf(70, 70, 70)
            (stayMins.sum() <= 210) shouldBe true
        }

        test("maxStops 를 넘으면 앞에서부터 자른다") {
            val fixture = Fixture()
            fixture.responds(response(stop(1, 30), stop(2, 30), stop(3, 30), stop(4, 30), stop(5, 30), stop(6, 30)))

            fixture.composer
                .compose(condition, candidates)
                .stops
                .map { it.popup.id } shouldBe listOf(1L, 2L, 3L, 4L, 5L)
        }

        test("title · summary · reason 은 trim 후 길이 초과분을 자른다") {
            val fixture = Fixture()
            fixture.responds(
                response(
                    stop(1, reason = " ${"이".repeat(70)} "),
                    stop(2),
                    stop(3),
                    title = " ${"가".repeat(25)} ",
                    summary = "나".repeat(90),
                ),
            )

            val draft = fixture.composer.compose(condition, candidates)

            draft.title shouldBe "가".repeat(20)
            draft.summary shouldBe "나".repeat(80)
            draft.stops[0].reason shouldBe "이".repeat(60)
        }

        test("후보가 minStops 미만이면 호출 없이 IllegalArgumentException") {
            val fixture = Fixture()

            shouldThrow<IllegalArgumentException> { fixture.composer.compose(condition, candidates.take(2)) }
            verify(exactly = 0) { fixture.chatClient.complete(any(), any(), any(), any()) }
        }
    })
