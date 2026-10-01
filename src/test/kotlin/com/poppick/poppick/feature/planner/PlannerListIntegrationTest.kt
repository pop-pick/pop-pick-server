package com.poppick.poppick.feature.planner

import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity
import com.poppick.poppick.feature.member.dataaccess.repository.MemberRepository
import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.repository.PlannerRepository
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.feature.planner.domain.PlannerListCursor
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerSummary
import com.poppick.poppick.feature.planner.domain.PlannerTabCounts
import com.poppick.poppick.feature.planner.implement.PlannerReader
import com.poppick.poppick.feature.planner.implement.PlannerWriter
import com.poppick.poppick.feature.planner.presentation.scheduler.DraftPurger
import com.poppick.poppick.global.util.KST
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * "내 일정" 목록 · 건수 · DRAFT 정리 쿼리를 실 DB 로 확인한다(수동 검증용). 픽스처는 직접 저장하고 @Transactional 로 롤백된다.
 * local 프로파일(DB_*) 과 planner · planner_popup 테이블이 필요하다. 방문지는 popup_id NULL 로 저장해 popup 에 의존하지 않는다.
 */
@Disabled("수동 실행 전용: 실 DB 쓰기(롤백)")
@SpringBootTest
@ActiveProfiles("local")
class PlannerListIntegrationTest {
    @Autowired
    lateinit var plannerRepository: PlannerRepository

    @Autowired
    lateinit var plannerReader: PlannerReader

    @Autowired
    lateinit var plannerWriter: PlannerWriter

    @Autowired
    lateinit var draftPurger: DraftPurger

    @Autowired
    lateinit var memberRepository: MemberRepository

    private val today = LocalDate.now(KST)

    @Test
    @Transactional
    fun `tab 별 결과 · 정렬 · 커서 이어 읽기 · stopCount · firstStop`() {
        val memberKey = newMember()
        // 같은 날 두 일정: start_time 으로 순서가 갈린다.
        val soonLate = save(memberKey, PlannerStatus.SCHEDULED, today.plusDays(2), LocalTime.of(15, 0), stops = 3)
        val soonEarly = save(memberKey, PlannerStatus.SCHEDULED, today.plusDays(2), LocalTime.of(10, 0), stops = 2)
        val later = save(memberKey, PlannerStatus.SCHEDULED, today.plusDays(9), LocalTime.of(14, 0), stops = 4)
        val past = save(memberKey, PlannerStatus.SCHEDULED, today.minusDays(3), LocalTime.of(14, 0), stops = 3)
        val canceled = save(memberKey, PlannerStatus.CANCELED, today.plusDays(1), LocalTime.of(14, 0), stops = 2)
        save(memberKey, PlannerStatus.DRAFT, today.plusDays(1), LocalTime.of(14, 0), stops = 3)

        // UPCOMING 을 2건씩 두 페이지로
        val first = plannerReader.list(memberKey, PlannerListTab.UPCOMING, null, 2, today)
        first.content.map { it.id } shouldBe listOf(soonEarly, soonLate)
        first.hasNext shouldBe true
        val second =
            plannerReader.list(
                memberKey,
                PlannerListTab.UPCOMING,
                PlannerListCursor.parse(PlannerListTab.UPCOMING, first.nextCursor!!),
                2,
                today,
            )
        second.content.map { it.id } shouldBe listOf(later)
        second.hasNext shouldBe false
        second.nextCursor shouldBe null

        first.content.map { it.stopCount } shouldBe listOf(2, 3)
        first.content[0].firstStop shouldBe PlannerSummary.FirstStop("팝업 1", null)

        plannerReader.list(memberKey, PlannerListTab.PAST, null, 20, today).content.map { it.id } shouldBe listOf(past)

        val canceledPage = plannerReader.list(memberKey, PlannerListTab.CANCELED, null, 20, today)
        canceledPage.content.map { it.id } shouldBe listOf(canceled)
        canceledPage.content
            .single()
            .canceledAt!!
            .toInstant() shouldBe CANCELED_AT.toInstant()
    }

    @Test
    @Transactional
    fun `countByTab 은 목록 탭 조건과 같고 DRAFT 는 세지 않는다`() {
        val memberKey = newMember()
        save(memberKey, PlannerStatus.SCHEDULED, today, LocalTime.of(10, 0), stops = 2)
        save(memberKey, PlannerStatus.SCHEDULED, today.plusDays(9), LocalTime.of(14, 0), stops = 2)
        save(memberKey, PlannerStatus.SCHEDULED, today.minusDays(3), LocalTime.of(14, 0), stops = 2)
        save(memberKey, PlannerStatus.CANCELED, today.plusDays(1), LocalTime.of(14, 0), stops = 2)
        save(memberKey, PlannerStatus.DRAFT, today.plusDays(1), LocalTime.of(14, 0), stops = 2)

        plannerReader.countByTab(memberKey, today) shouldBe PlannerTabCounts(upcoming = 2, past = 1, canceled = 1)
        plannerReader.countByTab(newMember(), today) shouldBe PlannerTabCounts(0, 0, 0)
    }

    @Test
    @Transactional
    fun `saveDraft 는 회원의 기존 DRAFT 를 지우고 새 DRAFT 하나만 남긴다`() {
        val memberKey = newMember()
        val oldDrafts = (1..2).map { save(memberKey, PlannerStatus.DRAFT, today.plusDays(1), LocalTime.of(14, 0), stops = 2) }
        val scheduled = save(memberKey, PlannerStatus.SCHEDULED, today.plusDays(2), LocalTime.of(14, 0), stops = 2)
        val canceled = save(memberKey, PlannerStatus.CANCELED, today.plusDays(2), LocalTime.of(14, 0), stops = 2)
        val otherMembersDraft = save(newMember(), PlannerStatus.DRAFT, today.plusDays(1), LocalTime.of(14, 0), stops = 2)

        val saved = plannerWriter.saveDraft(draft(memberKey, OffsetDateTime.now(KST)))

        val drafts = plannerRepository.findAll().filter { it.memberKey == memberKey && it.status == PlannerStatus.DRAFT }
        drafts.map { it.id } shouldBe listOf(saved.id)
        oldDrafts.forEach { plannerRepository.findById(it).isPresent shouldBe false }
        plannerRepository.findById(scheduled).isPresent shouldBe true
        plannerRepository.findById(canceled).isPresent shouldBe true
        plannerRepository.findById(otherMembersDraft).isPresent shouldBe true
        plannerRepository.findByIdWithStops(saved.id!!)!!.stops.size shouldBe 3
    }

    @Test
    @Transactional
    fun `DraftPurger 는 24시간 지난 DRAFT 만 지운다`() {
        val memberKey = newMember()
        val now = OffsetDateTime.now(KST)
        val expired = saveEntity(draft(memberKey, now.minusHours(25)))
        val fresh = saveEntity(draft(memberKey, now.minusHours(1)))
        val scheduled = saveEntity(draft(memberKey, now.minusHours(25)).copy(status = PlannerStatus.SCHEDULED))

        draftPurger.purge()

        plannerRepository.findById(expired).isPresent shouldBe false
        plannerRepository.findById(fresh).isPresent shouldBe true
        plannerRepository.findById(scheduled).isPresent shouldBe true
    }

    private fun newMember() =
        memberRepository
            .saveAndFlush(
                MemberEntity(memberKey = UUID.randomUUID().toString(), email = "planner-it@example.com"),
            ).memberKey

    private fun draft(
        memberKey: String,
        createdAt: OffsetDateTime,
    ) = PlannerFixtures
        .planner(id = null, memberKey = memberKey, visitDate = today.plusDays(1))
        .let { base -> base.copy(createdAt = createdAt, stops = base.stops.map { it.copy(id = null, popupId = null) }) }

    private fun saveEntity(planner: Planner) = plannerRepository.saveAndFlush(PlannerEntity.from(planner)).id!!

    private fun save(
        memberKey: String,
        status: PlannerStatus,
        visitDate: LocalDate,
        startTime: LocalTime,
        stops: Int,
    ): Long {
        val base = PlannerFixtures.planner(id = null, memberKey = memberKey, status = status, visitDate = visitDate)
        val planner =
            base.copy(
                startTime = startTime,
                canceledAt = if (status == PlannerStatus.CANCELED) CANCELED_AT else null,
                stops = (1..stops).map { PlannerFixtures.stop(it, last = it == stops).copy(id = null, popupId = null) },
            )
        return plannerRepository.saveAndFlush(PlannerEntity.from(planner)).id!!
    }

    companion object {
        private val CANCELED_AT: OffsetDateTime = OffsetDateTime.now(KST).truncatedTo(ChronoUnit.MILLIS)
    }
}
