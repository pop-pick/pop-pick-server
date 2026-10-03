package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.dataaccess.client.perplexity.PerplexityAgentClient
import com.poppick.poppick.feature.popup.dataaccess.client.perplexity.PerplexityClientException
import com.poppick.poppick.feature.popup.dataaccess.client.web.ImageProbeClient
import com.poppick.poppick.feature.popup.dataaccess.client.web.OgImageClient
import com.poppick.poppick.feature.popup.domain.ImagePrompt
import com.poppick.poppick.feature.popup.domain.PageMeta
import com.poppick.poppick.feature.popup.domain.PerplexityImageResult
import com.poppick.poppick.feature.popup.domain.PerplexityUsage
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupImageCollector.Source
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executor

class PopupImageCollectorTest :
    FunSpec({
        val now = OffsetDateTime.of(2026, 10, 3, 5, 30, 0, 0, ZoneOffset.ofHours(9))
        val today = LocalDate.of(2026, 10, 3)
        val sameThread = Executor { it.run() }

        val popga = "https://popga.co.kr/popup/1"
        val newsis = "https://www.newsis.com/view/NISX1"
        val kakaoPlace = "http://place.map.kakao.com/1001"
        val poster = "https://img.example.com/poster.jpg"
        val usage = PerplexityUsage(costUsd = 0.004)

        fun popup(
            id: Long = 7,
            sourceUrls: List<String> = listOf(kakaoPlace, popga),
        ) = Popup(
            id = id,
            source = SourceType.KAKAO_MAP,
            title = "망그러진 곰 팝업스토어",
            brand = "망그러진 곰",
            sourceUrls = sourceUrls,
            enrichedAt = now.minusDays(1),
        )

        fun modelResult(
            imageUrl: String?,
            candidates: List<String> = listOf(poster, "https://img.example.com/map.png"),
        ) = PerplexityImageResult(imageUrl, candidates, usage)

        class Fixture {
            val popupReader = mockk<PopupReader>()
            val popupWriter = mockk<PopupWriter>(relaxed = true)
            val ogImageClient = mockk<OgImageClient>()
            val imageProbeClient = mockk<ImageProbeClient>()
            val perplexityAgentClient = mockk<PerplexityAgentClient>()
            val rateLimiter = mockk<RateLimiter>(relaxed = true)
            val collector =
                PopupImageCollector(
                    popupReader = popupReader,
                    popupWriter = popupWriter,
                    ogImageClient = ogImageClient,
                    imageProbeClient = imageProbeClient,
                    perplexityAgentClient = perplexityAgentClient,
                    rateLimiter = rateLimiter,
                    collectionProperties = Fixtures.collectionProperties(),
                    executor = sameThread,
                )

            init {
                every { ogImageClient.isAllowed(any()) } answers { firstArg<String>().startsWith("https://") }
                every { ogImageClient.fetch(any()) } returns null
                every { imageProbeClient.isImage(any()) } returns true
            }
        }

        test("og:image 를 얻으면 Perplexity 는 호출하지 않는다") {
            val f = Fixture()
            every { f.ogImageClient.fetch(popga) } returns PageMeta("https://cdn.popga.co.kr/1.jpg", "[팝가] 망그러진곰 팝업 스토어 성수")

            val outcome = f.collector.collectOne(popup(), today, now)

            outcome.source shouldBe Source.OG
            verify(exactly = 0) { f.ogImageClient.fetch(kakaoPlace) }
            verify(exactly = 0) { f.perplexityAgentClient.findImage(any()) }
            verify(exactly = 0) { f.rateLimiter.acquire() }
            verify { f.popupWriter.updateImages(7, listOf("https://cdn.popga.co.kr/1.jpg"), now) }
        }

        test("og 에서 못 얻으면 속도 제한 후 Perplexity 를 부르고, 모델 주소가 검색 결과에 있고 이미지로 확인되면 저장한다") {
            val f = Fixture()
            every { f.ogImageClient.fetch(popga) } returns PageMeta("https://cdn.popga.co.kr/other.jpg", "다른 브랜드 팝업")
            every { f.perplexityAgentClient.findImage(ImagePrompt.build(popup(), today)) } returns modelResult(poster)
            val usages = mutableListOf<PerplexityUsage>()

            val outcome = f.collector.collectOne(popup(), today, now) { usages += it }

            outcome.source shouldBe Source.MODEL
            outcome.rejected shouldBe false
            usages shouldBe listOf(usage)
            verifyOrder {
                f.rateLimiter.acquire()
                f.perplexityAgentClient.findImage(any())
                f.imageProbeClient.isImage(poster)
            }
            verify { f.popupWriter.updateImages(7, listOf(poster), now) }
        }

        test("모델 주소가 검색 결과에 없으면 지어낸 주소로 보고 버린다(다른 후보로 대체하지 않는다)") {
            val f = Fixture()
            every { f.perplexityAgentClient.findImage(any()) } returns modelResult("https://img.example.com/poster.jpg?w=800")

            val outcome = f.collector.collectOne(popup(), today, now)

            outcome.source shouldBe Source.NONE
            outcome.rejected shouldBe true
            verify(exactly = 0) { f.imageProbeClient.isImage(any()) }
            verify { f.popupWriter.updateImages(7, emptyList(), now) }
        }

        test("이미지 확인에 실패하면 버린다") {
            val f = Fixture()
            every { f.perplexityAgentClient.findImage(any()) } returns modelResult(poster)
            every { f.imageProbeClient.isImage(poster) } returns false

            val outcome = f.collector.collectOne(popup(), today, now)

            outcome.source shouldBe Source.NONE
            outcome.rejected shouldBe true
            verify { f.popupWriter.updateImages(7, emptyList(), now) }
        }

        test("모델이 null 을 주면 못 찾은 것으로 image_checked_at 만 갱신한다") {
            val f = Fixture()
            every { f.perplexityAgentClient.findImage(any()) } returns modelResult(null)

            val outcome = f.collector.collectOne(popup(), today, now)

            outcome.source shouldBe Source.NONE
            outcome.rejected shouldBe false
            verify { f.popupWriter.updateImages(7, emptyList(), now) }
        }

        test("Perplexity 예외는 그대로 던지고 image_checked_at 을 갱신하지 않으며, 응답을 받은 건은 비용을 넘긴다") {
            val f = Fixture()
            every { f.perplexityAgentClient.findImage(any()) } throws
                PerplexityClientException("응답 잘림", usage = usage, incompleteReason = "max_output_tokens")
            val usages = mutableListOf<PerplexityUsage>()

            shouldThrow<PerplexityClientException> { f.collector.collectOne(popup(), today, now) { usages += it } }

            usages shouldBe listOf(usage)
            verify(exactly = 0) { f.popupWriter.updateImages(any(), any(), any()) }
        }

        test("og 에서 여러 장을 찾을 수 있어도 1장만 저장하고 남은 출처 페이지는 읽지 않는다") {
            val f = Fixture()
            every { f.ogImageClient.fetch(popga) } returns PageMeta("https://cdn.popga.co.kr/1.jpg", "망그러진 곰")
            every { f.ogImageClient.fetch(newsis) } returns PageMeta("https://img.newsis.com/1.jpg", "망그러진 곰")

            f.collector.collectOne(popup(sourceUrls = listOf(popga, newsis)), today, now)

            verify(exactly = 0) { f.ogImageClient.fetch(newsis) }
            verify { f.popupWriter.updateImages(7, listOf("https://cdn.popga.co.kr/1.jpg"), now) }
        }

        test("run 은 대상별 결과 · 버린 건수 · 비용(실패 건 포함)을 집계하고 1건 실패는 로그 후 계속한다") {
            val f = Fixture()
            every { f.popupReader.findImageTargets(today, any(), 100) } returns
                listOf(popup(1), popup(2, emptyList()), popup(3, emptyList()), popup(4, emptyList()), popup(5, emptyList()))
            every { f.ogImageClient.fetch(popga) } returns PageMeta("https://cdn.popga.co.kr/1.jpg", "망그러진 곰")
            every { f.perplexityAgentClient.findImage(any()) } returns
                modelResult(poster) andThen
                modelResult("https://made.up/1.jpg") andThen
                modelResult(null) andThenThrows
                PerplexityClientException("Perplexity 재시도 소진 status=503")

            val report = f.collector.run(today)

            report.targets shouldBe 5
            report.found shouldBe 2
            report.fromOg shouldBe 1
            report.fromModel shouldBe 1
            report.notFound shouldBe 2
            report.rejected shouldBe 1
            report.failed shouldBe 1
            report.costUsd shouldBe (0.012 plusOrMinus 1e-9)
            report.timedOut shouldBe false
        }
    })
