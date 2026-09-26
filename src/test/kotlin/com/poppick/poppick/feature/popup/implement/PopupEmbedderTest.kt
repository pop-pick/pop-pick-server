package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.member.domain.InterestCategory
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.dataaccess.client.openai.OpenAiClientException
import com.poppick.poppick.feature.popup.dataaccess.client.openai.OpenAiEmbeddingClient
import com.poppick.poppick.feature.popup.domain.EmbeddingResult
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupEmbedding
import com.poppick.poppick.feature.popup.domain.PopupProfileText
import com.poppick.poppick.feature.popup.domain.SourceType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.LocalDate

class PopupEmbedderTest :
    FunSpec({
        val today = LocalDate.of(2026, 9, 26)
        val model = "text-embedding-3-small"

        fun popup(id: Long) =
            Popup(
                id = id,
                source = SourceType.KAKAO_MAP,
                title = "팝업$id",
                interestCategoryId = 1,
                startDate = today,
                endDate = today.plusDays(7),
            )

        fun textOf(id: Long) = PopupProfileText.build(popup(id), "캐릭터/IP")

        fun embedding(
            id: Long,
            hash: String,
        ) = PopupEmbedding(id, PopupEmbedding.KIND_PROFILE, model, "old", hash, floatArrayOf(0f))

        class Fixture(
            batchSize: Int = 32,
            embedTimeout: Duration = Duration.ofMinutes(5),
        ) {
            val popupReader = mockk<PopupReader>()
            val popupEmbeddingReader = mockk<PopupEmbeddingReader>()
            val popupEmbeddingWriter = mockk<PopupEmbeddingWriter>(relaxed = true)
            val client = mockk<OpenAiEmbeddingClient>()
            val upserted = mutableListOf<List<PopupEmbedding>>()
            val embedder =
                PopupEmbedder(
                    popupReader = popupReader,
                    popupEmbeddingReader = popupEmbeddingReader,
                    popupEmbeddingWriter = popupEmbeddingWriter,
                    interestCategoryReader =
                        mockk<InterestCategoryReader> {
                            every { findAll() } returns listOf(InterestCategory(1, "캐릭터/IP"))
                        },
                    openAiEmbeddingClient = client,
                    openAiProperties = Fixtures.openAiProperties(batchSize = batchSize),
                    collectionProperties = Fixtures.collectionProperties(embedTimeout = embedTimeout),
                )

            init {
                every { popupEmbeddingWriter.upsert(capture(upserted)) } returns Unit
                every { client.embed(any()) } answers {
                    val texts = firstArg<List<String>>()
                    EmbeddingResult(texts.map { floatArrayOf(1f) }, promptTokens = texts.size * 10, costUsd = texts.size * 0.0001)
                }
            }

            fun targets(vararg ids: Long) = every { popupReader.findEmbedTargets(today) } returns ids.map { popup(it) }

            fun existing(vararg embeddings: PopupEmbedding) =
                every { popupEmbeddingReader.findAll(any(), PopupEmbedding.KIND_PROFILE, model) } returns embeddings.toList()
        }

        test("해시가 같으면 건너뛰고, 다르면 갱신 · 없으면 새로 저장한다") {
            val fixture = Fixture()
            fixture.targets(1, 2, 3)
            fixture.existing(
                embedding(1, PopupProfileText.sha256(textOf(1))),
                embedding(2, "stale-hash"),
            )

            val report = fixture.embedder.run(today)

            verify(exactly = 1) { fixture.client.embed(listOf(textOf(2), textOf(3))) }
            with(fixture.upserted.single()) {
                map { it.popupId } shouldContainExactly listOf(2L, 3L)
                map { it.contentHash } shouldContainExactly listOf(PopupProfileText.sha256(textOf(2)), PopupProfileText.sha256(textOf(3)))
                all { it.kind == PopupEmbedding.KIND_PROFILE && it.model == model } shouldBe true
            }
            report.targets shouldBe 3
            report.embedded shouldBe 2
            report.skipped shouldBe 1
            report.failed shouldBe 0
            report.promptTokens shouldBe 20
            report.costUsd shouldBe (0.0002 plusOrMinus 1e-12)
        }

        test("기간 · 카테고리가 없는 팝업도 대상이다(카테고리 줄 없이 임베딩)") {
            val fixture = Fixture()
            val bare = Popup(id = 9, source = SourceType.KAKAO_MAP, title = "팝업9")
            every { fixture.popupReader.findEmbedTargets(today) } returns listOf(popup(1), bare)
            fixture.existing()

            val report = fixture.embedder.run(today)

            verify(exactly = 1) { fixture.client.embed(listOf(textOf(1), "팝업9")) }
            fixture.upserted.single().map { it.popupId } shouldContainExactly listOf(1L, 9L)
            report.targets shouldBe 2
            report.embedded shouldBe 2
        }

        test("전부 해시가 같으면 호출하지 않는다") {
            val fixture = Fixture()
            fixture.targets(1)
            fixture.existing(embedding(1, PopupProfileText.sha256(textOf(1))))

            val report = fixture.embedder.run(today)

            verify(exactly = 0) { fixture.client.embed(any()) }
            fixture.upserted.shouldBeEmpty()
            report.skipped shouldBe 1
            report.embedded shouldBe 0
        }

        test("배치 하나가 실패해도 나머지 배치는 진행한다") {
            val fixture = Fixture(batchSize = 2)
            fixture.targets(1, 2, 3, 4, 5)
            fixture.existing()
            every { fixture.client.embed(listOf(textOf(3), textOf(4))) } throws OpenAiClientException("OpenAI 재시도 소진 status=503")

            val report = fixture.embedder.run(today)

            verify(exactly = 3) { fixture.client.embed(any()) }
            fixture.upserted.map { batch -> batch.map { it.popupId } } shouldContainExactly listOf(listOf(1L, 2L), listOf(5L))
            report.embedded shouldBe 3
            report.failed shouldBe 2
            report.skipped shouldBe 0
            report.timedOut shouldBe false
        }

        test("시간을 넘기면 남은 배치는 skipped 로 세고 timedOut") {
            val fixture = Fixture(batchSize = 2, embedTimeout = Duration.ZERO)
            fixture.targets(1, 2, 3)
            fixture.existing()

            val report = fixture.embedder.run(today)

            verify(exactly = 0) { fixture.client.embed(any()) }
            report.skipped shouldBe 3
            report.timedOut shouldBe true
        }

        test("요약은 비용을 소수 넷째 자리로 찍는다") {
            val fixture = Fixture()
            fixture.targets(1)
            fixture.existing()

            fixture.embedder
                .run(today)
                .summary()
                .startsWith("embed: targets=1 embedded=1 skipped=0 failed=0 tokens=10 cost=\$0.0001 in ") shouldBe true
        }
    })
