package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.entity.QPopupEntity.popupEntity
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport
import java.time.OffsetDateTime

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

    override fun findEnrichTargets(
        retryLimit: Int,
        retryBefore: OffsetDateTime,
        limit: Int,
    ): List<PopupEntity> =
        selectFrom(popupEntity)
            .where(
                popupEntity.enrichedAt.isNull.or(
                    popupEntity.enrichRetryCount
                        .lt(retryLimit)
                        .and(popupEntity.enrichedAt.lt(retryBefore))
                        .and(
                            popupEntity.startDate.isNull
                                .or(popupEntity.endDate.isNull)
                                .or(popupEntity.interestCategoryId.isNull),
                        ),
                ),
            ).orderBy(popupEntity.id.asc())
            .limit(limit.toLong())
            .fetch()
}
