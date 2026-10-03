package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.member.domain.InterestCategory
import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.dataaccess.client.perplexity.PerplexityAgentClient
import com.poppick.poppick.feature.popup.dataaccess.client.perplexity.PerplexityClientException
import com.poppick.poppick.feature.popup.domain.EnrichTarget
import com.poppick.poppick.feature.popup.domain.EnrichTargetCriteria
import com.poppick.poppick.feature.popup.domain.PerplexityEnrichResult
import com.poppick.poppick.feature.popup.domain.PerplexityUsage
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupEnrichment
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.util.KST
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.Executor

class PopupEnricherTest :
    FunSpec({
        val today = LocalDate.of(2026, 9, 25)
        val sameThread = Executor { it.run() }

        fun popup(id: Long) = Popup(id = id, source = SourceType.KAKAO_MAP, title = "팝업$id", placeName = "팝업$id")

        class Fixture {
            val properties = Fixtures.collectionProperties(enrichTimeout = Duration.ofMinutes(1), perplexityThreads = 1)
            val popupReader = mockk<PopupReader>()
            val popupWriter = mockk<PopupWriter>()
            val perplexityAgentClient = mockk<PerplexityAgentClient>()
            val popupEnrichmentMerger = spyk(PopupEnrichmentMerger())
            val enricher =
                PopupEnricher(
                    popupReader = popupReader,
                    popupWriter = popupWriter,
                    interestCategoryReader =
                        mockk<InterestCategoryReader> {
                            every { findAll() } returns
                                listOf(InterestCategory(1, "캐릭터/IP"))
                        },
                    favoriteAreaReader =
                        mockk<FavoriteAreaReader> {
                            every { findAll() } returns Fixtures.favoriteAreas
                        },
                    perplexityAgentClient = perplexityAgentClient,
                    popupEnrichmentMerger = popupEnrichmentMerger,
                    rateLimiter = mockk(relaxed = true),
                    collectionProperties = properties,
                    executor = sameThread,
                )

            init {
                every { popupReader.findById(any()) } answers { popup(firstArg()) }
                every { popupWriter.save(any()) } answers { firstArg() }
            }

            fun targets(vararg ids: Long) =
                every { popupReader.findEnrichTargets(any()) } returns ids.map { EnrichTarget(it, refresh = false) }
        }

        val badRequest = PerplexityClientException("Perplexity 요청 실패 status=400", statusCode = 400)
        val notFound = PerplexityEnrichResult(PopupEnrichment(found = false, matchesPlace = true), emptyList())

        test("4xx 가 5건 연속이면 남은 대상을 제출하지 않는다") {
            val fixture = Fixture()
            fixture.targets(*LongArray(10) { it + 1L })
            every { fixture.perplexityAgentClient.enrich(any(), any()) } throws badRequest

            val report = fixture.enricher.enrich(today)

            verify(exactly = 5) { fixture.perplexityAgentClient.enrich(any(), any()) }
            report.targets shouldBe 10
            report.failed shouldBe 5
            report.skipped shouldBe 5
        }

        test("성공이 섞이면 카운터를 리셋한다") {
            val fixture = Fixture()
            fixture.targets(*LongArray(11) { it + 1L })
            // 4xx 4건 → 성공 → 4xx 5건: 앞의 4건은 리셋되므로 10번째까지 제출하고 11번째를 건너뛴다.
            every { fixture.perplexityAgentClient.enrich(any(), any()) } throws badRequest andThenThrows badRequest andThenThrows
                badRequest andThenThrows badRequest andThen notFound andThenThrows badRequest

            val report = fixture.enricher.enrich(today)

            verify(exactly = 10) { fixture.perplexityAgentClient.enrich(any(), any()) }
            report.notFound shouldBe 1
            report.failed shouldBe 9
            report.skipped shouldBe 1
        }

        test("응답이 온 건의 비용 · 검색 횟수를 성공 · 실패 무관하게 합산한다") {
            val fixture = Fixture()
            fixture.targets(1, 2, 3)
            val usage = PerplexityUsage(costUsd = 0.01, inputTokens = 100, outputTokens = 50, searchCalls = 2)
            every { fixture.perplexityAgentClient.enrich(any(), any()) } returns
                notFound.copy(usage = usage) andThenThrows
                PerplexityClientException("응답 잘림", usage = usage.copy(costUsd = 0.02), incompleteReason = "max_output_tokens") andThenThrows
                PerplexityClientException("Perplexity 요청 실패 status=400", statusCode = 400)

            val report = fixture.enricher.enrich(today)

            report.failed shouldBe 2
            report.costUsd shouldBe (0.03 plusOrMinus 1e-9)
            report.searchCalls shouldBe 4
        }

        test("favorite_area 를 이름 → id 맵으로 만들어 merge 에 넘긴다") {
            val fixture = Fixture()
            fixture.targets(1)
            every { fixture.perplexityAgentClient.enrich(any(), any()) } returns
                PerplexityEnrichResult(PopupEnrichment(found = true, matchesPlace = true, area = "홍대"), emptyList())
            val areas = mapOf("성수" to 1, "여의도" to 2, "홍대" to 3, "잠실" to 4, "용산" to 5, "종로" to 6, "강남" to 7)

            fixture.enricher.enrich(today)

            verify { fixture.popupEnrichmentMerger.merge(any(), any(), any(), areas, any()) }
            verify { fixture.popupWriter.save(match { it.areaId == 3 }) }
        }

        test("4xx 가 아닌 실패(재시도 소진 · 파싱 실패)는 세지 않는다") {
            val fixture = Fixture()
            fixture.targets(*LongArray(7) { it + 1L })
            every { fixture.perplexityAgentClient.enrich(any(), any()) } throws PerplexityClientException("Perplexity 재시도 소진 status=503")

            val report = fixture.enricher.enrich(today)

            report.failed shouldBe 7
            report.skipped shouldBe 0
        }

        test("미보강 · 재시도 · 진행 중 갱신 조건으로 대상을 고르고, 갱신 사유 대상 수를 refreshed 로 센다") {
            val fixture = Fixture()
            val criteria = slot<EnrichTargetCriteria>()
            every { fixture.popupReader.findEnrichTargets(capture(criteria)) } returns
                listOf(EnrichTarget(1, refresh = false), EnrichTarget(2, refresh = true), EnrichTarget(3, refresh = true))
            every { fixture.perplexityAgentClient.enrich(any(), any()) } returns notFound
            val before = OffsetDateTime.now(KST)

            val report = fixture.enricher.enrich(today)

            with(criteria.captured) {
                retryLimit shouldBe 2
                this.today shouldBe today
                imminentUntil shouldBe today.plusDays(7)
                limit shouldBe 200
                Duration.between(retryBefore, refreshBefore) shouldBe Duration.ofDays(-7)
                Duration.between(imminentRefreshBefore, retryBefore) shouldBe Duration.ofDays(-4)
                (refreshBefore <= before.minusDays(14).plusMinutes(1)) shouldBe true
            }
            report.targets shouldBe 3
            report.refreshed shouldBe 2
        }
    })
