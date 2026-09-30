package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.repository.PlannerRepository
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger { }

@Component
class PlannerWriter(
    private val plannerRepository: PlannerRepository,
) {
    /**
     * 회원의 기존 DRAFT 를 지우고 planner 1건 + stops N건을 저장한다(한 트랜잭션). 회원당 DRAFT 는 최대 1개.
     * 외부 호출(임베딩 · LLM · 길찾기)이 실패하면 여기까지 오지 않으므로 기존 DRAFT 는 그대로 남는다(의도된 동작).
     */
    @Transactional
    fun saveDraft(planner: Planner): Planner {
        val removed = plannerRepository.deleteDrafts(planner.memberKey)
        val saved = plannerRepository.save(PlannerEntity.from(planner)).toDomain()
        log.info { "planner draft replaced: memberKey=${planner.memberKey} removed=$removed newId=${saved.id}" }
        return saved
    }

    /** created_at < olderThan 인 DRAFT 를 물리 삭제하고 건수를 돌려준다. */
    @Transactional
    fun purgeDrafts(olderThan: OffsetDateTime): Long = plannerRepository.deleteDraftsCreatedBefore(olderThan)

    /** SCHEDULED 로 바꾸고 confirmed_at 을 기록한다. 상태 · 소유자 검증은 호출 측이 한다. */
    @Transactional
    fun confirm(
        id: Long,
        now: OffsetDateTime,
    ): Planner {
        val entity = plannerRepository.findByIdWithStops(id) ?: throw AppException(ErrorType.PLANNER_NOT_FOUND)
        entity.confirm(now)
        return entity.toDomain()
    }

    /** SCHEDULED → CANCELED. canceled_at 은 목록 커서(epoch ms)와 맞게 ms 단위로 자른다. 상태 · 소유자 검증은 호출 측. */
    @Transactional
    fun cancel(
        id: Long,
        now: OffsetDateTime,
    ): Planner {
        val entity = plannerRepository.findByIdWithStops(id) ?: throw AppException(ErrorType.PLANNER_NOT_FOUND)
        entity.cancel(now.truncatedTo(ChronoUnit.MILLIS))
        return entity.toDomain()
    }

    /** 물리 삭제(DRAFT 폐기). planner_popup 은 FK CASCADE · JPA cascade 로 함께 지워진다. */
    @Transactional
    fun delete(id: Long) {
        plannerRepository.deleteById(id)
    }
}
