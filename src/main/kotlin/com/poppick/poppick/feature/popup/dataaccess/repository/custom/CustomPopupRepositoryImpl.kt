package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.entity.QPopupEntity.popupEntity
import com.poppick.poppick.feature.popup.domain.PopupStatus
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport
import java.time.LocalDate

class CustomPopupRepositoryImpl :
    QuerydslRepositorySupport(PopupEntity::class),
    CustomPopupRepository {
    override fun findBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): PopupEntity? =
        selectFrom(popupEntity)
            .where(
                popupEntity.source.eq(source),
                popupEntity.externalId.eq(externalId),
            ).fetchOne()

    override fun findAllByStatusInAndEndDateBefore(
        statuses: Collection<PopupStatus>,
        date: LocalDate,
    ): List<PopupEntity> {
        if (statuses.isEmpty()) return emptyList()

        return selectFrom(popupEntity)
            .where(
                popupEntity.status.`in`(statuses),
                popupEntity.endDate.lt(date),
            ).fetch()
    }

    override fun findMatchCandidates(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        statusesExcluded: Collection<PopupStatus>,
    ): List<PopupEntity> =
        selectFrom(popupEntity)
            .where(
                popupEntity.latitude.between(minLat, maxLat),
                popupEntity.longitude.between(minLon, maxLon),
                statusNotIn(statusesExcluded),
            ).fetch()

    override fun findAllByPlaceIdAndStatusNotIn(
        placeId: String,
        statuses: Collection<PopupStatus>,
    ): List<PopupEntity> =
        selectFrom(popupEntity)
            .where(
                popupEntity.placeId.eq(placeId),
                statusNotIn(statuses),
            ).fetch()

    // 빈 컬렉션이면 조건을 생략한다(where 는 null 을 무시).
    private fun statusNotIn(statuses: Collection<PopupStatus>) = statuses.takeIf { it.isNotEmpty() }?.let { popupEntity.status.notIn(it) }
}
