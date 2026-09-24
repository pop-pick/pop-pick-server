package com.poppick.poppick.feature.popup.business

import com.poppick.poppick.feature.popup.domain.CollectionReport
import com.poppick.poppick.feature.popup.implement.KakaoPopupCollector
import com.poppick.poppick.feature.popup.implement.PopupEnricher
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
                timedOut = false,
                elapsed = Duration.ZERO,
            )

        test("collect 가 실패해도 enrich 는 실행된다") {
            val collector = mockk<KakaoPopupCollector>()
            val enricher = mockk<PopupEnricher>()
            every { collector.collect() } throws RuntimeException("kakao down")
            every { enricher.enrich(any()) } returns enrichReport

            val report = PopupCollectionService(collector, enricher).run()

            verify(exactly = 1) { enricher.enrich(any()) }
            report.collect.shouldBeNull()
            report.enrich shouldBe enrichReport
        }

        test("두 단계가 모두 성공하면 단계별 결과를 담는다") {
            val collector = mockk<KakaoPopupCollector>(relaxed = true)
            val enricher = mockk<PopupEnricher>()
            every { enricher.enrich(any()) } returns enrichReport

            val report = PopupCollectionService(collector, enricher).run()

            report.collect.shouldNotBeNull()
            report.enrich shouldBe enrichReport
        }
    })
