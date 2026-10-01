package com.poppick.poppick.feature.wish.implement

import com.poppick.poppick.feature.popup.implement.PopupReader
import com.poppick.poppick.feature.wish.WishFixtures
import com.poppick.poppick.feature.wish.WishFixtures.MEMBER_KEY
import com.poppick.poppick.feature.wish.dataaccess.entity.PopupWishEntity
import com.poppick.poppick.feature.wish.dataaccess.repository.PopupWishRepository
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

class WishReaderTest :
    FunSpec({
        val repository = mockk<PopupWishRepository>()
        val popupReader = mockk<PopupReader>()
        val reader = WishReader(repository, popupReader)
        val today = LocalDate.of(2026, 10, 2)

        fun entity(
            id: Long,
            popupId: Long,
        ) = PopupWishEntity(memberKey = MEMBER_KEY, popupId = popupId, createdAt = WishFixtures.WISHED_AT, id = id)

        beforeTest { clearAllMocks() }

        test("찜 순서를 유지하고 팝업은 한 번에 조회해 붙이며 ended 를 계산한다") {
            val cursorable = Cursorable<Long>(null, 2)
            every { repository.findByMemberKey(MEMBER_KEY, cursorable) } returns
                Slice(mutableListOf(entity(7, 30), entity(5, 10)), cursorable, true)
            // findAllByIds 는 ids 순서대로 돌려주지만, 붙이는 쪽은 순서에 기대지 않는다.
            every { popupReader.findAllByIds(listOf(30, 10)) } returns
                listOf(WishFixtures.popup(10, endDate = today.minusDays(1)), WishFixtures.popup(30))

            val slice = reader.findWishedPopups(MEMBER_KEY, today, cursorable)

            slice.content.map { it.wish.id to it.popup.id } shouldBe listOf(7L to 30L, 5L to 10L)
            slice.content.map { it.ended } shouldBe listOf(false, true)
            slice.hasNext shouldBe true
            verify(exactly = 1) { popupReader.findAllByIds(any()) }
        }

        test("팝업이 없어진 찜은 빠진다") {
            val cursorable = Cursorable<Long>(null, 10)
            every { repository.findByMemberKey(MEMBER_KEY, cursorable) } returns
                Slice(mutableListOf(entity(2, 20), entity(1, 10)), cursorable, false)
            every { popupReader.findAllByIds(listOf(20, 10)) } returns listOf(WishFixtures.popup(10))

            reader.findWishedPopups(MEMBER_KEY, today, cursorable).content.map { it.popup.id } shouldBe listOf(10L)
        }

        test("찜이 없으면 빈 목록") {
            val cursorable = Cursorable<Long>(null, 10)
            every { repository.findByMemberKey(MEMBER_KEY, cursorable) } returns Slice(mutableListOf(), cursorable, false)
            every { popupReader.findAllByIds(emptyList()) } returns emptyList()

            val slice = reader.findWishedPopups(MEMBER_KEY, today, cursorable)

            slice.content shouldBe emptyList()
            slice.hasNext shouldBe false
        }

        test("findWishedPopupIds 는 저장소 결과를 그대로 돌려준다") {
            every { repository.findWishedPopupIds(MEMBER_KEY, listOf(1L, 2L, 3L)) } returns setOf(2L)

            reader.findWishedPopupIds(MEMBER_KEY, listOf(1L, 2L, 3L)) shouldBe setOf(2L)
        }

        test("findWishedPopupIds 는 빈 입력이면 조회하지 않고 빈 Set") {
            reader.findWishedPopupIds(MEMBER_KEY, emptyList()) shouldBe emptySet()

            verify(exactly = 0) { repository.findWishedPopupIds(any(), any()) }
        }
    })
