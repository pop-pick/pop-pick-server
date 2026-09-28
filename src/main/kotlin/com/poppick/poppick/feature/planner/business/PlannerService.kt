package com.poppick.poppick.feature.planner.business

import com.poppick.poppick.config.properties.PlannerProperties
import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.member.implement.MemberPreferenceReader
import com.poppick.poppick.feature.member.implement.PreferredActivityReader
import com.poppick.poppick.feature.planner.domain.GoogleCalendarLink
import com.poppick.poppick.feature.planner.domain.IcsBuilder
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.feature.planner.domain.PlannerDetail
import com.poppick.poppick.feature.planner.domain.PlannerForm
import com.poppick.poppick.feature.planner.domain.PlannerGenerateCommand
import com.poppick.poppick.feature.planner.domain.PlannerListCursor
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import com.poppick.poppick.feature.planner.domain.PlannerPolicy
import com.poppick.poppick.feature.planner.domain.PlannerShare
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerSummaryPage
import com.poppick.poppick.feature.planner.implement.PlannerReader
import com.poppick.poppick.feature.planner.implement.PlannerWriter
import com.poppick.poppick.feature.planner.implement.ShareTokenGenerator
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.util.KST
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

private val log = KotlinLogging.logger { }

/**
 * 플래너 API 진입점. 생성 · 확정 · 소유자 조회는 PlannerGenerator 에 맡기고(소유자 · 상태 검사 재사용),
 * 목록 · 취소 · 공유 · 캘린더와 응답용 지역 이름 붙이기를 한다.
 */
@Service
class PlannerService(
    private val plannerGenerator: PlannerGenerator,
    private val plannerReader: PlannerReader,
    private val plannerWriter: PlannerWriter,
    private val shareTokenGenerator: ShareTokenGenerator,
    private val plannerProperties: PlannerProperties,
    private val memberPreferenceReader: MemberPreferenceReader,
    private val favoriteAreaReader: FavoriteAreaReader,
    private val interestCategoryReader: InterestCategoryReader,
    private val preferredActivityReader: PreferredActivityReader,
    private val clock: Clock = Clock.system(KST),
) {
    fun form(memberKey: String): PlannerForm {
        val preference = memberPreferenceReader.find(memberKey)
        val today = LocalDate.now(clock)
        return PlannerForm(
            defaultAreaId = preference.favoriteAreaIds.firstOrNull(),
            defaultInterestCategoryIds = preference.interestCategoryIds,
            defaultPreferredActivityIds = preference.preferredActivityIds,
            areas = favoriteAreaReader.findAll().sortedBy { it.id },
            interestCategories = interestCategoryReader.findAll().sortedBy { it.id },
            preferredActivities = preferredActivityReader.findAll().sortedBy { it.id },
            visitDateMin = today,
            visitDateMax = today.plusDays(PlannerPolicy.MAX_DAYS_AHEAD),
        )
    }

    fun generate(
        memberKey: String,
        command: PlannerGenerateCommand,
    ) = detail(plannerGenerator.generate(memberKey, command))

    fun confirm(
        memberKey: String,
        plannerId: Long,
    ) = detail(plannerGenerator.confirm(memberKey, plannerId))

    fun get(
        memberKey: String,
        plannerId: Long,
    ) = detail(plannerGenerator.get(memberKey, plannerId))

    /** "내 일정" 목록. cursor 형식이 tab 과 맞지 않으면 INVALID_REQUEST. */
    fun list(
        memberKey: String,
        tab: PlannerListTab,
        cursorable: Cursorable<String>,
    ): PlannerSummaryPage {
        val cursor = cursorable.cursor?.takeIf { it.isNotBlank() }?.let { PlannerListCursor.parse(tab, it) }
        val page = plannerReader.list(memberKey, tab, cursor, cursorable.limit, LocalDate.now(clock))
        val areaNames = areaNames()
        return page.copy(content = page.content.map { it.copy(areaName = areaNames[it.areaId]) })
    }

    /** SCHEDULED → CANCELED(지난 일정 포함), DRAFT → 물리 삭제, CANCELED → INVALID_PLANNER_STATUS. 복구는 없다. */
    fun cancel(
        memberKey: String,
        plannerId: Long,
    ) {
        val planner = plannerGenerator.get(memberKey, plannerId)
        when (planner.status) {
            PlannerStatus.SCHEDULED -> plannerWriter.cancel(plannerId, OffsetDateTime.now(clock))
            PlannerStatus.DRAFT -> plannerWriter.delete(plannerId)
            PlannerStatus.CANCELED -> throw AppException(ErrorType.INVALID_PLANNER_STATUS)
        }
        log.info { "planner canceled: id=$plannerId memberKey=$memberKey status=${planner.status}" }
    }

    /** SCHEDULED 만 공유할 수 있다. 토큰이 있으면 재사용(멱등), 없으면 발급. 토큰은 로그에 남기지 않는다. */
    fun share(
        memberKey: String,
        plannerId: Long,
    ): PlannerShare {
        val planner = plannerGenerator.get(memberKey, plannerId)
        if (planner.status != PlannerStatus.SCHEDULED) throw AppException(ErrorType.INVALID_PLANNER_STATUS)

        val token =
            planner.shareToken ?: try {
                plannerWriter.assignShareToken(plannerId, shareTokenGenerator.generate())
            } catch (e: DataIntegrityViolationException) {
                // 다른 플래너와 토큰이 겹친 경우(확률상 거의 없음). 1회만 재생성한다.
                log.warn { "planner share token 충돌, 재생성 id=$plannerId" }
                plannerWriter.assignShareToken(plannerId, shareTokenGenerator.generate())
            }
        log.info { "planner shared: id=$plannerId" }
        return PlannerShare(token, shareUrl(token))
    }

    /** 비로그인 공유 조회. 토큰이 없거나 취소된 일정이면 PLANNER_NOT_FOUND. 지난 일정은 보여준다. */
    fun getShared(token: String) = detail(sharedPlanner(token))

    /** 구글 캘린더 추가 링크(소유자). */
    fun calendar(
        memberKey: String,
        plannerId: Long,
    ): String {
        val planner = plannerGenerator.get(memberKey, plannerId)
        return GoogleCalendarLink.build(planner, areaName(planner), planner.shareToken?.let { shareUrl(it) })
    }

    /** .ics 본문(소유자). */
    fun calendarIcs(
        memberKey: String,
        plannerId: Long,
    ): String {
        val planner = plannerGenerator.get(memberKey, plannerId)
        return IcsBuilder.build(planner, areaName(planner), planner.shareToken?.let { shareUrl(it) }, clock.instant())
    }

    fun sharedCalendar(token: String): String {
        val planner = sharedPlanner(token)
        return GoogleCalendarLink.build(planner, areaName(planner), shareUrl(token))
    }

    fun sharedCalendarIcs(token: String): String {
        val planner = sharedPlanner(token)
        return IcsBuilder.build(planner, areaName(planner), shareUrl(token), clock.instant())
    }

    private fun sharedPlanner(token: String): Planner {
        val planner = plannerReader.findByShareToken(token) ?: throw AppException(ErrorType.PLANNER_NOT_FOUND)
        // 취소한 일정은 공유 링크도 죽는다. DRAFT 는 토큰이 발급되지 않지만 방어.
        if (planner.status != PlannerStatus.SCHEDULED) throw AppException(ErrorType.PLANNER_NOT_FOUND)
        return planner
    }

    private fun shareUrl(token: String) = "${plannerProperties.shareBaseUrl.trimEnd('/')}/$token"

    private fun areaNames() = favoriteAreaReader.findAll().associate { it.id to it.area }

    private fun areaName(planner: Planner) = areaNames()[planner.areaId].orEmpty()

    private fun detail(planner: Planner) = PlannerDetail(planner, areaNames()[planner.areaId])
}
