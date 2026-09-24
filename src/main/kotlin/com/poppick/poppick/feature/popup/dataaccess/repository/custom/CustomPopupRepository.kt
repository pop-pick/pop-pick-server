package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.domain.PopupStatus
import com.poppick.poppick.feature.popup.domain.SourceType
import java.time.LocalDate

interface CustomPopupRepository {
    fun findBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): PopupEntity?

    fun findAllByStatusInAndEndDateBefore(
        statuses: Collection<PopupStatus>,
        date: LocalDate,
    ): List<PopupEntity>

    fun findMatchCandidates(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        statusesExcluded: Collection<PopupStatus>,
    ): List<PopupEntity>

    fun findAllByPlaceIdAndStatusNotIn(
        placeId: String,
        statuses: Collection<PopupStatus>,
    ): List<PopupEntity>
}
