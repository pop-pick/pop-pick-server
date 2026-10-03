package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.EnrichTarget
import com.poppick.poppick.feature.popup.domain.EnrichTargetCriteria
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 노출 필터는 상태가 아니라 `end_date IS NULL OR end_date >= today` 로 조회 측에서 결정한다.
 */
@Component
class PopupReader(
    private val popupRepository: PopupRepository,
) {
    fun findEnrichTargets(criteria: EnrichTargetCriteria): List<EnrichTarget> =
        popupRepository.findEnrichTargets(criteria).mapNotNull { entity ->
            entity.id?.let { EnrichTarget(it, criteria.isRefresh(entity.toDomain())) }
        }

    fun findImageTargets(
        today: LocalDate,
        retryBefore: OffsetDateTime,
        limit: Int,
    ): List<Popup> = popupRepository.findImageTargets(today, retryBefore, limit).map { it.toDomain() }

    fun findEmbedTargets(today: LocalDate): List<Popup> = popupRepository.findEmbedTargets(today).map { it.toDomain() }

    fun findById(id: Long): Popup = popupRepository.findByIdOrNull(id)?.toDomain() ?: throw AppException(ErrorType.NOT_FOUND_DATA)

    /** ids 순서대로 반환한다. 없는 id 는 빠진다. */
    fun findAllByIds(ids: List<Long>): List<Popup> {
        if (ids.isEmpty()) return emptyList()
        val byId = popupRepository.findAllById(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }
}
