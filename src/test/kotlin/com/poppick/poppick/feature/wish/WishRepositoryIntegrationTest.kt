package com.poppick.poppick.feature.wish

import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity
import com.poppick.poppick.feature.member.dataaccess.repository.MemberRepository
import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.wish.business.WishService
import com.poppick.poppick.feature.wish.dataaccess.repository.PopupWishRepository
import com.poppick.poppick.feature.wish.implement.WishReader
import com.poppick.poppick.feature.wish.implement.WishWriter
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.util.KST
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 찜 저장(ON CONFLICT) · 해제 · 목록 keyset · 찜 여부 조회를 실 DB 로 확인한다(수동 검증용).
 * 픽스처는 직접 저장하고 @Transactional 로 롤백된다. local 프로파일(DB_*) 과 popup_wish 테이블이 필요하다.
 */
@Disabled("수동 실행 전용: 실 DB 쓰기(롤백)")
@SpringBootTest
@ActiveProfiles("local")
class WishRepositoryIntegrationTest {
    @Autowired
    lateinit var popupWishRepository: PopupWishRepository

    @Autowired
    lateinit var wishWriter: WishWriter

    @Autowired
    lateinit var wishReader: WishReader

    @Autowired
    lateinit var wishService: WishService

    @Autowired
    lateinit var popupRepository: PopupRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    private val today = LocalDate.now(KST)
    private val now = OffsetDateTime.now(KST)

    @Test
    @Transactional
    fun `등록 - 신규는 1행, 같은 팝업 두 번째는 영향 행 0 이고 행은 1개`() {
        val memberKey = member()
        val popupId = popup("wish-it-dup")

        wishWriter.add(memberKey, popupId, now) shouldBe 1
        wishWriter.add(memberKey, popupId, now) shouldBe 0

        count(memberKey) shouldBe 1
    }

    @Test
    @Transactional
    fun `등록 - 없는 팝업은 NOT_FOUND_DATA, 종료된 팝업은 등록된다`() {
        val memberKey = member()
        val ended = popup("wish-it-ended", endDate = today.minusDays(3))

        shouldThrow<AppException> { wishService.wish(memberKey, Long.MAX_VALUE) }.errorType shouldBe ErrorType.NOT_FOUND_DATA
        wishService.wish(memberKey, ended)

        wishReader.findWishedPopupIds(memberKey, listOf(ended)) shouldBe setOf(ended)
    }

    @Test
    @Transactional
    fun `해제 - 찜한 팝업은 지우고, 없는 대상은 예외 없이 0, 다른 회원 찜은 남는다`() {
        val memberKey = member()
        val otherKey = member()
        val popupId = popup("wish-it-remove")
        wishWriter.add(memberKey, popupId, now)
        wishWriter.add(otherKey, popupId, now)

        wishWriter.remove(memberKey, popupId) shouldBe 1
        wishWriter.remove(memberKey, popupId) shouldBe 0
        wishWriter.remove(memberKey, Long.MAX_VALUE) shouldBe 0

        count(memberKey) shouldBe 0
        count(otherKey) shouldBe 1
    }

    @Test
    @Transactional
    fun `목록 - 최근 찜한 순 · 커서 이어 읽기 · 종료 팝업 포함 · 다른 회원 찜 제외`() {
        val memberKey = member()
        val otherKey = member()
        val (a, b, c) = List(3) { popup("wish-it-list-$it") }
        val ended = popup("wish-it-list-ended", endDate = today.minusDays(1))
        listOf(a, b, ended, c).forEach { wishWriter.add(memberKey, it, now) }
        wishWriter.add(otherKey, a, now)

        val first = wishService.findWishes(memberKey, Cursorable(null, 2))
        first.content.map { it.popup.id } shouldBe listOf(c, ended)
        first.content.map { it.ended } shouldBe listOf(false, true)
        first.hasNext shouldBe true

        val second =
            wishService.findWishes(
                memberKey,
                Cursorable(
                    first.content
                        .last()
                        .wish.id,
                    2,
                ),
            )
        second.content.map { it.popup.id } shouldBe listOf(b, a)
        second.hasNext shouldBe false
    }

    @Test
    @Transactional
    fun `목록 - 찜이 없으면 빈 목록`() {
        val slice = wishService.findWishes(member(), Cursorable(null, 10))

        slice.content shouldBe emptyList()
        slice.hasNext shouldBe false
    }

    @Test
    @Transactional
    fun `findWishedPopupIds - 찜한 id 만, 빈 입력은 빈 Set`() {
        val memberKey = member()
        val (a, b, c) = List(3) { popup("wish-it-ids-$it") }
        wishWriter.add(memberKey, a, now)
        wishWriter.add(memberKey, c, now)

        wishReader.findWishedPopupIds(memberKey, listOf(a, b, c)) shouldBe setOf(a, c)
        wishReader.findWishedPopupIds(memberKey, emptyList()) shouldBe emptySet()
    }

    private fun count(memberKey: String) = popupWishRepository.findAll().count { it.memberKey == memberKey }

    private fun member() =
        memberRepository.saveAndFlush(MemberEntity(memberKey = UUID.randomUUID().toString(), email = "wish-it@example.com")).memberKey

    private fun popup(
        externalId: String,
        endDate: LocalDate? = today.plusDays(10),
    ) = popupRepository
        .saveAndFlush(
            PopupEntity(
                source = SourceType.KAKAO_MAP,
                externalId = "$externalId-${UUID.randomUUID()}",
                title = externalId,
                endDate = endDate,
            ),
        ).id!!
}
