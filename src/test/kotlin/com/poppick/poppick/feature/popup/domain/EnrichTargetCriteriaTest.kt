package com.poppick.poppick.feature.popup.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class EnrichTargetCriteriaTest :
    FunSpec({
        val now = OffsetDateTime.of(2026, 10, 3, 5, 30, 0, 0, ZoneOffset.ofHours(9))
        val today = LocalDate.of(2026, 10, 3)
        val criteria =
            EnrichTargetCriteria(
                retryLimit = 2,
                retryBefore = now.minusDays(7),
                today = today,
                refreshBefore = now.minusDays(14),
                imminentUntil = today.plusDays(7),
                imminentRefreshBefore = now.minusDays(3),
                limit = 200,
            )
        val complete =
            Popup(
                id = 1,
                source = SourceType.KAKAO_MAP,
                title = "팝업",
                startDate = today.minusDays(10),
                endDate = today.plusDays(30),
                interestCategoryId = 1,
                areaId = 1,
                enrichedAt = now.minusDays(20),
            )

        test("미보강 팝업은 갱신 사유가 아니다") {
            criteria.isRefresh(complete.copy(enrichedAt = null)) shouldBe false
        }

        test("핵심 필드 공백 재시도에 해당하면 갱신 사유가 아니다") {
            criteria.isRefresh(complete.copy(areaId = null)) shouldBe false
        }

        test("핵심 필드가 다 차 있거나 재시도 한도에 닿았으면 갱신 사유다") {
            criteria.isRefresh(complete) shouldBe true
            criteria.isRefresh(complete.copy(areaId = null, enrichRetryCount = 2)) shouldBe true
        }
    })
