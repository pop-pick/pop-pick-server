package com.poppick.poppick.feature.popup.business

import com.poppick.poppick.feature.popup.domain.CollectionReport
import com.poppick.poppick.feature.popup.implement.KakaoPopupCollector
import com.poppick.poppick.feature.popup.implement.PopupEmbedder
import com.poppick.poppick.feature.popup.implement.PopupEnricher
import com.poppick.poppick.feature.popup.implement.PopupImageCollector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Duration

class PopupCollectionServiceTest :
    FunSpec({
        val collectReport =
            CollectionReport.Collect(
                keywords = 30,
                failedKeywords = 1,
                partialKeywords = 2,
                truncatedKeywords = 3,
                fetched = 900,
                seoul = 850,
                unique = 400,
                created = 10,
                updated = 390,
                failed = 0,
                timedOut = false,
                elapsed = Duration.ZERO,
            )
        val enrichReport =
            CollectionReport.Enrich(
                targets = 1,
                refreshed = 1,
                enriched = 1,
                notFound = 0,
                failed = 0,
                skipped = 0,
                active = 1,
                ended = 0,
                incomplete = 0,
                costUsd = 0.01,
                searchCalls = 2,
                timedOut = false,
                elapsed = Duration.ZERO,
            )
        val imageReport =
            CollectionReport.Image(
                targets = 5,
                found = 3,
                notFound = 1,
                failed = 1,
                fromOg = 2,
                fromModel = 1,
                rejected = 1,
                costUsd = 0.0123,
                timedOut = false,
                elapsed = Duration.ZERO,
            )
        val embedReport =
            CollectionReport.Embed(
                targets = 13,
                embedded = 13,
                skipped = 0,
                failed = 0,
                promptTokens = 2600,
                costUsd = 0.000052,
                timedOut = false,
                elapsed = Duration.ZERO,
            )

        test("collect 요약에 일부 실패 · 잘린 검색어 수를 찍는다") {
            collectReport.summary() shouldStartWith
                "collect: keywords=30 failedKeywords=1 partialKeywords=2 truncatedKeywords=3 fetched=900 seoul=850 unique=400 " +
                "new=10 updated=390 failed=0 in "
        }

        test("enrich 요약에 갱신 대상 수 · 비용(소수 둘째 자리) · 검색 횟수를 찍는다") {
            enrichReport.summary() shouldStartWith
                "enrich: targets=1 refreshed=1 enriched=1 notFound=0 failed=0 skipped=0 active=1 ended=0 incomplete=0 " +
                "cost=\$0.01 searches=2 in "
        }

        test("image 요약에 출처별 건수 · 버린 건수 · 비용(소수 둘째 자리)을 찍는다") {
            imageReport.summary() shouldStartWith
                "image: targets=5 found=3 notFound=1 failed=1 fromOg=2 fromModel=1 rejected=1 cost=\$0.01 in "
            imageReport.copy(timedOut = true).summary() shouldContain " TIMED_OUT in "
        }

        test("embed 요약에 토큰 · 비용(소수 넷째 자리)을 찍는다") {
            embedReport.summary() shouldStartWith "embed: targets=13 embedded=13 skipped=0 failed=0 tokens=2600 cost=\$0.0001 in "
        }

        test("collect 가 실패해도 enrich · embed 는 실행된다") {
            val collector = mockk<KakaoPopupCollector>()
            val enricher = mockk<PopupEnricher>()
            val imageCollector = mockk<PopupImageCollector>()
            val embedder = mockk<PopupEmbedder>()
            every { collector.collect(any()) } throws RuntimeException("kakao down")
            every { enricher.enrich(any()) } returns enrichReport
            every { imageCollector.run(any()) } returns imageReport
            every { embedder.run(any()) } returns embedReport

            val report = PopupCollectionService(collector, enricher, imageCollector, embedder).run()

            verify(exactly = 1) { enricher.enrich(any()) }
            verify(exactly = 1) { imageCollector.run(any()) }
            verify(exactly = 1) { embedder.run(any()) }
            report.collect.shouldBeNull()
            report.enrich shouldBe enrichReport
            report.embed shouldBe embedReport
        }

        test("enrich 가 실패해도 image · embed 는 실행된다") {
            val collector = mockk<KakaoPopupCollector>(relaxed = true)
            val enricher = mockk<PopupEnricher>()
            val imageCollector = mockk<PopupImageCollector>()
            val embedder = mockk<PopupEmbedder>()
            every { enricher.enrich(any()) } throws RuntimeException("perplexity down")
            every { imageCollector.run(any()) } returns imageReport
            every { embedder.run(any()) } returns embedReport

            val report = PopupCollectionService(collector, enricher, imageCollector, embedder).run()

            verify(exactly = 1) { imageCollector.run(any()) }
            verify(exactly = 1) { embedder.run(any()) }
            report.enrich.shouldBeNull()
            report.image shouldBe imageReport
            report.embed shouldBe embedReport
            report.summary() shouldContain "enrich=failed image=ok embed=ok"
        }

        test("image 가 실패해도 embed 는 실행된다") {
            val collector = mockk<KakaoPopupCollector>(relaxed = true)
            val enricher = mockk<PopupEnricher>()
            val imageCollector = mockk<PopupImageCollector>()
            val embedder = mockk<PopupEmbedder>()
            every { enricher.enrich(any()) } returns enrichReport
            every { imageCollector.run(any()) } throws RuntimeException("kakao image down")
            every { embedder.run(any()) } returns embedReport

            val report = PopupCollectionService(collector, enricher, imageCollector, embedder).run()

            verify(exactly = 1) { embedder.run(any()) }
            report.image.shouldBeNull()
            report.summary() shouldContain "enrich=ok image=failed embed=ok"
        }

        test("collect → enrich → image → embed 순서로 실행하고 단계별 결과를 담는다") {
            val collector = mockk<KakaoPopupCollector>()
            val enricher = mockk<PopupEnricher>()
            val imageCollector = mockk<PopupImageCollector>()
            val embedder = mockk<PopupEmbedder>()
            every { collector.collect(any()) } returns collectReport
            every { enricher.enrich(any()) } returns enrichReport
            every { imageCollector.run(any()) } returns imageReport
            every { embedder.run(any()) } returns embedReport

            val report = PopupCollectionService(collector, enricher, imageCollector, embedder).run()

            verifyOrder {
                collector.collect(any())
                enricher.enrich(report.date)
                imageCollector.run(report.date)
                embedder.run(report.date)
            }
            report.collect.shouldNotBeNull()
            report.enrich shouldBe enrichReport
            report.image shouldBe imageReport
            report.embed shouldBe embedReport
            report.summary() shouldContain "collect=ok enrich=ok image=ok embed=ok in "
        }
    })
