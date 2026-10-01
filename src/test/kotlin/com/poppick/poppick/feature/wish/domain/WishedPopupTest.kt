package com.poppick.poppick.feature.wish.domain

import com.poppick.poppick.feature.wish.WishFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class WishedPopupTest :
    FunSpec({
        val today = LocalDate.of(2026, 10, 2)
        val wish = WishFixtures.wish(id = 1, popupId = 10)

        test("종료일이 오늘보다 앞이면 ended") {
            WishedPopup.of(wish, WishFixtures.popup(10, endDate = today.minusDays(1)), today).ended shouldBe true
        }

        test("종료일이 오늘이거나 이후면 ended 가 아니다") {
            WishedPopup.of(wish, WishFixtures.popup(10, endDate = today), today).ended shouldBe false
            WishedPopup.of(wish, WishFixtures.popup(10, endDate = today.plusDays(1)), today).ended shouldBe false
        }

        test("종료일이 없으면 ended 가 아니다") {
            WishedPopup.of(wish, WishFixtures.popup(10, endDate = null), today).ended shouldBe false
        }
    })
