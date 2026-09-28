package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.config.properties.PlannerProperties
import com.poppick.poppick.feature.member.domain.MemberPreference
import com.poppick.poppick.feature.member.implement.MemberPreferenceReader
import com.poppick.poppick.feature.planner.domain.CandidateCondition
import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.dataaccess.client.openai.OpenAiEmbeddingClient
import com.poppick.poppick.feature.popup.domain.EmbeddingResult
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSimilarity
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupEmbeddingReader
import com.poppick.poppick.feature.popup.implement.PopupReader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

class CandidatePopupFinderTest :
    FunSpec({
        val visitDate = LocalDate.of(2026, 10, 3)
        val vector = floatArrayOf(0.1f, 0.2f)
        val model = Fixtures.openAiProperties().embeddingModel

        fun popup(id: Long) = Popup(id = id, source = SourceType.KAKAO_MAP, title = "팝업$id")

        class Fixture(
            preference: MemberPreference = MemberPreference(listOf("캐릭터/IP"), listOf("사진 찍기")),
            hits: List<PopupSimilarity> = emptyList(),
        ) {
            val embeddingClient = mockk<OpenAiEmbeddingClient>()
            val popupEmbeddingReader = mockk<PopupEmbeddingReader>()
            val popupReader = mockk<PopupReader>()
            val finder =
                CandidatePopupFinder(
                    memberPreferenceReader = mockk<MemberPreferenceReader> { every { find(any()) } returns preference },
                    openAiEmbeddingClient = embeddingClient,
                    popupEmbeddingReader = popupEmbeddingReader,
                    popupReader = popupReader,
                    openAiProperties = Fixtures.openAiProperties(),
                    plannerProperties = PlannerProperties(candidateLimit = 40),
                )

            init {
                every { embeddingClient.embed(any()) } returns EmbeddingResult(listOf(vector), promptTokens = 10, costUsd = 0.0)
                every { popupEmbeddingReader.searchSimilar(any(), any(), any(), any(), any(), any()) } returns hits
                // 리포지토리는 순서를 보장하지 않으므로 뒤집어 돌려준다.
                every { popupReader.findAllByIds(any()) } answers { firstArg<List<Long>>().reversed().map { popup(it) } }
            }
        }

        fun condition(
            note: String? = null,
            exclude: Set<Long> = emptySet(),
        ) = CandidateCondition(memberKey = "member-1", areaId = 3, visitDate = visitDate, note = note, excludePopupIds = exclude)

        test("쿼리 텍스트가 있으면 embed 1회 후 그 벡터로 검색한다") {
            val fixture = Fixture()

            fixture.finder.find(condition(note = "산리오"))

            verify(exactly = 1) { fixture.embeddingClient.embed(listOf("카테고리: 캐릭터/IP\n체험 · 키워드: 사진 찍기\n산리오")) }
            verify { fixture.popupEmbeddingReader.searchSimilar(vector, model, 3, visitDate, emptySet(), 40) }
        }

        test("쿼리 텍스트가 NULL 이면 embed 없이 queryVector=null 로 검색한다") {
            val fixture = Fixture(preference = MemberPreference(emptyList(), emptyList()))

            fixture.finder.find(condition())

            verify(exactly = 0) { fixture.embeddingClient.embed(any()) }
            verify { fixture.popupEmbeddingReader.searchSimilar(null, model, 3, visitDate, emptySet(), 40) }
        }

        test("검색 결과 순서대로 Popup 이 매핑된다") {
            val hits = listOf(PopupSimilarity(7, 0.1), PopupSimilarity(3, 0.4), PopupSimilarity(9, 0.8))
            val fixture = Fixture(hits = hits)

            val candidates = fixture.finder.find(condition())

            candidates.map { it.popup.id } shouldBe listOf(7L, 3L, 9L)
            candidates.map { it.distance } shouldBe listOf(0.1, 0.4, 0.8)
        }

        test("excludePopupIds 를 그대로 넘긴다") {
            val fixture = Fixture()

            fixture.finder.find(condition(exclude = setOf(1L, 2L)))

            verify { fixture.popupEmbeddingReader.searchSimilar(vector, model, 3, visitDate, setOf(1L, 2L), 40) }
        }

        test("검색 결과가 없으면 빈 리스트") {
            Fixture().finder.find(condition()).shouldBeEmpty()
        }
    })
