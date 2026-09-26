package com.poppick.poppick.feature.popup.business

import com.poppick.poppick.feature.popup.domain.CollectionReport
import com.poppick.poppick.feature.popup.implement.KakaoPopupCollector
import com.poppick.poppick.feature.popup.implement.PopupEmbedder
import com.poppick.poppick.feature.popup.implement.PopupEnricher
import com.poppick.poppick.feature.popup.implement.PopupPurger
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
        val enrichReport =
            CollectionReport.Enrich(
                targets = 1,
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

        test("enrich 요약에 비용(소수 둘째 자리) · 검색 횟수를 찍는다") {
            enrichReport.summary() shouldStartWith
                "enrich: targets=1 enriched=1 notFound=0 failed=0 skipped=0 active=1 ended=0 incomplete=0 cost=\$0.01 searches=2 in "
        }

        val purgeReport = CollectionReport.Purge(popups = 40, embeddings = 12, elapsed = Duration.ZERO)

        fun purger() = mockk<PopupPurger>().also { every { it.run(any()) } returns purgeReport }

        test("purge 요약에 팝업 · 임베딩 삭제 건수를 찍는다") {
            purgeReport.summary() shouldStartWith "purge: popups=40 embeddings=12 in "
        }

        test("purge 가 실패해도 collect · enrich · embed 는 실행된다") {
            val purger = mockk<PopupPurger>()
            val collector = mockk<KakaoPopupCollector>(relaxed = true)
            val enricher = mockk<PopupEnricher>()
            val embedder = mockk<PopupEmbedder>()
            every { purger.run(any()) } throws RuntimeException("db down")
            every { enricher.enrich(any()) } returns enrichReport
            every { embedder.run(any()) } returns embedReport

            val report = PopupCollectionService(purger, collector, enricher, embedder).run()

            verify(exactly = 1) { collector.collect() }
            verify(exactly = 1) { enricher.enrich(any()) }
            verify(exactly = 1) { embedder.run(any()) }
            report.purge.shouldBeNull()
            report.summary() shouldContain "purge=failed collect=ok enrich=ok embed=ok"
        }

        test("embed 요약에 토큰 · 비용(소수 넷째 자리)을 찍는다") {
            embedReport.summary() shouldStartWith "embed: targets=13 embedded=13 skipped=0 failed=0 tokens=2600 cost=\$0.0001 in "
        }

        test("collect 가 실패해도 enrich · embed 는 실행된다") {
            val purger = purger()
            val collector = mockk<KakaoPopupCollector>()
            val enricher = mockk<PopupEnricher>()
            val embedder = mockk<PopupEmbedder>()
            every { collector.collect() } throws RuntimeException("kakao down")
            every { enricher.enrich(any()) } returns enrichReport
            every { embedder.run(any()) } returns embedReport

            val report = PopupCollectionService(purger, collector, enricher, embedder).run()

            verify(exactly = 1) { enricher.enrich(any()) }
            verify(exactly = 1) { embedder.run(any()) }
            report.collect.shouldBeNull()
            report.enrich shouldBe enrichReport
            report.embed shouldBe embedReport
        }

        test("enrich 가 실패해도 embed 는 실행된다") {
            val purger = purger()
            val collector = mockk<KakaoPopupCollector>(relaxed = true)
            val enricher = mockk<PopupEnricher>()
            val embedder = mockk<PopupEmbedder>()
            every { enricher.enrich(any()) } throws RuntimeException("perplexity down")
            every { embedder.run(any()) } returns embedReport

            val report = PopupCollectionService(purger, collector, enricher, embedder).run()

            verify(exactly = 1) { embedder.run(any()) }
            report.enrich.shouldBeNull()
            report.embed shouldBe embedReport
            report.summary() shouldContain "enrich=failed embed=ok"
        }

        test("purge → collect → enrich → embed 순서로 실행하고 단계별 결과를 담는다") {
            val purger = purger()
            val collector = mockk<KakaoPopupCollector>(relaxed = true)
            val enricher = mockk<PopupEnricher>()
            val embedder = mockk<PopupEmbedder>()
            every { enricher.enrich(any()) } returns enrichReport
            every { embedder.run(any()) } returns embedReport

            val report = PopupCollectionService(purger, collector, enricher, embedder).run()

            verifyOrder {
                purger.run(any())
                collector.collect()
                enricher.enrich(any())
                embedder.run(any())
            }
            report.purge shouldBe purgeReport
            report.collect.shouldNotBeNull()
            report.enrich shouldBe enrichReport
            report.embed shouldBe embedReport
            report.summary() shouldContain "purge=ok collect=ok enrich=ok embed=ok in "
        }
    })
