package com.poppick.poppick.feature.planner

import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity
import com.poppick.poppick.feature.member.dataaccess.repository.MemberRepository
import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.repository.PlannerRepository
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.util.KST
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * findScheduledPopupIds 를 실 DB 로 확인한다(수동 검증용). 픽스처는 직접 저장하고 @Transactional 로 롤백된다.
 * local 프로파일(DB_*) 과 planner · planner_popup 테이블이 필요하다.
 */
@Disabled("수동 실행 전용: 실 DB 쓰기(롤백)")
@SpringBootTest
@ActiveProfiles("local")
class PlannerRepositoryIntegrationTest {
    @Autowired
    lateinit var plannerRepository: PlannerRepository

    @Autowired
    lateinit var popupRepository: PopupRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Test
    @Transactional
    fun `findScheduledPopupIds 는 SCHEDULED 만 돌려주고 DRAFT · CANCELED · 다른 회원 · 삭제된 팝업은 뺀다`() {
        val memberKey = member()
        val otherKey = member()
        val (a, b, c, d, e) = List(5) { popup("repo-it-$it") }

        planner(memberKey, PlannerStatus.SCHEDULED, a, b, null)
        // 지난 일정도 포함
        planner(memberKey, PlannerStatus.SCHEDULED, c, visitDate = LocalDate.now(KST).minusDays(10))
        planner(memberKey, PlannerStatus.DRAFT, d)
        planner(memberKey, PlannerStatus.CANCELED, e)
        planner(otherKey, PlannerStatus.SCHEDULED, d)

        plannerRepository.findScheduledPopupIds(memberKey) shouldBe setOf(a, b, c)
    }

    @Test
    @Transactional
    fun `findByIdWithStops 는 stops 를 visit_order 순으로 읽는다`() {
        val memberKey = member()
        val (a, b, c) = List(3) { popup("repo-it-order-$it") }
        val id = planner(memberKey, PlannerStatus.DRAFT, a, b, c)
        plannerRepository.flush()

        plannerRepository
            .findByIdWithStops(id)!!
            .toDomain()
            .stops
            .map { it.popupId } shouldBe listOf(a, b, c)
    }

    private fun member() =
        memberRepository.saveAndFlush(MemberEntity(memberKey = UUID.randomUUID().toString(), email = "planner-it@example.com")).memberKey

    private fun popup(externalId: String) =
        popupRepository
            .saveAndFlush(
                PopupEntity(source = SourceType.KAKAO_MAP, externalId = externalId, title = externalId, latitude = 37.5, longitude = 127.0),
            ).id!!

    private fun planner(
        memberKey: String,
        status: PlannerStatus,
        vararg popupIds: Long?,
        visitDate: LocalDate = LocalDate.now(KST).plusDays(3),
    ): Long {
        val base = PlannerFixtures.planner(id = null, memberKey = memberKey, status = status, visitDate = visitDate)
        val stops = popupIds.mapIndexed { index, popupId -> PlannerFixtures.stop(index + 1).copy(id = null, popupId = popupId) }
        return plannerRepository.saveAndFlush(PlannerEntity.from(base.copy(stops = stops))).id!!
    }
}
