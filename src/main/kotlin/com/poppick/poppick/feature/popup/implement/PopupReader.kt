package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
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
    fun findEnrichTargetIds(
        retryLimit: Int,
        retryBefore: OffsetDateTime,
        limit: Int,
    ): List<Long> = popupRepository.findEnrichTargets(retryLimit, retryBefore, limit).mapNotNull { it.id }

    fun findEmbedTargets(today: LocalDate): List<Popup> = popupRepository.findEmbedTargets(today).map { it.toDomain() }

    fun findExpiredPopupIds(today: LocalDate): List<Long> = popupRepository.findExpiredPopupIds(today)

    fun findById(id: Long): Popup = popupRepository.findByIdOrNull(id)?.toDomain() ?: throw AppException(ErrorType.NOT_FOUND_DATA)
}
