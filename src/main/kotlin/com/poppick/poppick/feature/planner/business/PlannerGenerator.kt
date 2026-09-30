package com.poppick.poppick.feature.planner.business

import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.member.implement.PreferredActivityReader
import com.poppick.poppick.feature.planner.domain.CandidateCondition
import com.poppick.poppick.feature.planner.domain.CourseCondition
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.feature.planner.domain.PlannerGenerateCommand
import com.poppick.poppick.feature.planner.domain.PlannerPolicy
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.implement.CandidatePopupFinder
import com.poppick.poppick.feature.planner.implement.CourseComposer
import com.poppick.poppick.feature.planner.implement.CoursePlanner
import com.poppick.poppick.feature.planner.implement.PlannerReader
import com.poppick.poppick.feature.planner.implement.PlannerWriter
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.util.KST
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

private val log = KotlinLogging.logger { }

/**
 * 후보 검색 → LLM 코스 선정 → 도보 경로 · 방문 시각 → DRAFT 저장.
 * 외부 호출(임베딩 · LLM · 카카오)은 트랜잭션 밖에서 하고 저장만 트랜잭션(PlannerWriter)으로 묶는다.
 * 중간에 실패하면 아무것도 저장하지 않는다. CourseGenerationException · OpenAiClientException · RouteFailedException 은
 * 그대로 던지고 전역 핸들러가 GENERATION_FAILED · ROUTE_FAILED 로 바꾼다.
 */
@Service
class PlannerGenerator(
    private val favoriteAreaReader: FavoriteAreaReader,
    private val interestCategoryReader: InterestCategoryReader,
    private val preferredActivityReader: PreferredActivityReader,
    private val candidatePopupFinder: CandidatePopupFinder,
    private val courseComposer: CourseComposer,
    private val coursePlanner: CoursePlanner,
    private val plannerReader: PlannerReader,
    private val plannerWriter: PlannerWriter,
    private val clock: Clock = Clock.system(KST),
) {
    fun generate(
        memberKey: String,
        command: PlannerGenerateCommand,
    ): Planner {
        val startedAt = System.nanoTime()
        val now = OffsetDateTime.now(clock)
        validateVisitDate(command.visitDate, now.toLocalDate())
        validateStartTime(command.visitDate, command.startTime, now)
        if (favoriteAreaReader.findAll().none { it.id == command.areaId }) {
            throw AppException(ErrorType.INVALID_REQUEST, "존재하지 않는 지역 areaId=${command.areaId}")
        }
        val categories =
            namesOf(command.interestCategoryIds, interestCategoryReader.findAll().associate { it.id to it.category }, "interestCategoryIds")
        val activities =
            namesOf(
                command.preferredActivityIds,
                preferredActivityReader.findAll().associate { it.id to it.activity },
                "preferredActivityIds",
            )

        val candidates =
            candidatePopupFinder.find(
                CandidateCondition(
                    areaId = command.areaId,
                    visitDate = command.visitDate,
                    categories = categories,
                    activities = activities,
                    note = command.note,
                    excludePopupIds = plannerReader.findScheduledPopupIds(memberKey),
                ),
            )
        // LLM 호출 전에 끝낸다.
        if (candidates.size < command.durationType.minStops) throw AppException(ErrorType.INSUFFICIENT_POPUPS)

        val condition =
            CourseCondition(
                visitDate = command.visitDate,
                startTime = command.startTime,
                accompanyType = command.accompanyType,
                durationType = command.durationType,
                note = command.note,
            )
        val draft = courseComposer.compose(condition, candidates)
        val course =
            try {
                coursePlanner.plan(condition, draft)
            } catch (e: IllegalStateException) {
                // 늦은 시작 + 긴 코스로 자정을 넘기는 경우(VisitScheduler).
                throw AppException(ErrorType.INVALID_START_TIME, cause = e)
            }

        val planner = plannerWriter.saveDraft(Planner.draft(memberKey, command, course, now))
        log.info {
            "planner generated: id=${planner.id} memberKey=$memberKey areaId=${command.areaId} stops=${planner.stops.size} " +
                "elapsedMs=${(System.nanoTime() - startedAt) / 1_000_000}"
        }
        return planner
    }

    fun confirm(
        memberKey: String,
        plannerId: Long,
    ): Planner {
        val planner = get(memberKey, plannerId)
        if (planner.status != PlannerStatus.DRAFT) throw AppException(ErrorType.INVALID_PLANNER_STATUS)
        val now = OffsetDateTime.now(clock)
        // 오래 방치한 DRAFT.
        if (planner.visitDate < now.toLocalDate()) throw AppException(ErrorType.INVALID_VISIT_DATE)
        return plannerWriter.confirm(plannerId, now)
    }

    /** 소유자만. 상태 무관. */
    fun get(
        memberKey: String,
        plannerId: Long,
    ): Planner {
        val planner = plannerReader.get(plannerId)
        if (!planner.isOwnedBy(memberKey)) throw AppException(ErrorType.PLANNER_FORBIDDEN)
        return planner
    }

    private fun validateVisitDate(
        visitDate: LocalDate,
        today: LocalDate,
    ) {
        if (visitDate < today || visitDate > today.plusDays(PlannerPolicy.MAX_DAYS_AHEAD)) {
            throw AppException(ErrorType.INVALID_VISIT_DATE)
        }
    }

    private fun validateStartTime(
        visitDate: LocalDate,
        startTime: LocalTime,
        now: OffsetDateTime,
    ) {
        val inRange = startTime >= PlannerPolicy.START_TIME_MIN && startTime <= PlannerPolicy.START_TIME_MAX
        // 오늘 23:40 에 요청하면 +30분이 자정을 넘으므로 LocalTime 이 아니라 LocalDateTime 으로 비교한다.
        val earliest = now.toLocalDateTime().plusMinutes(PlannerPolicy.TODAY_LEAD_MINUTES)
        val tooSoon = visitDate == now.toLocalDate() && visitDate.atTime(startTime) < earliest
        if (!inRange || tooSoon) throw AppException(ErrorType.INVALID_START_TIME)
    }

    /** 요청 순서대로 이름으로 바꾼다. 없는 id 가 하나라도 있으면 INVALID_REQUEST. */
    private fun namesOf(
        ids: List<Int>,
        names: Map<Int, String>,
        field: String,
    ): List<String> {
        val unknown = ids.filterNot { it in names }
        if (unknown.isNotEmpty()) throw AppException(ErrorType.INVALID_REQUEST, "존재하지 않는 $field=$unknown")
        return ids.distinct().map { names.getValue(it) }
    }
}
