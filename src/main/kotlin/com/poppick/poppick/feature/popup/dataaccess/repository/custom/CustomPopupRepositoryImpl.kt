package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.entity.QPopupEntity.popupEntity
import com.poppick.poppick.feature.popup.domain.EnrichTargetCriteria
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport
import com.querydsl.core.types.dsl.Expressions
import java.time.LocalDate
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

    override fun findEnrichTargets(criteria: EnrichTargetCriteria): List<PopupEntity> {
        val unenriched = popupEntity.enrichedAt.isNull
        val retry =
            popupEntity.enrichRetryCount
                .lt(criteria.retryLimit)
                .and(popupEntity.enrichedAt.lt(criteria.retryBefore))
                .and(
                    popupEntity.startDate.isNull
                        .or(popupEntity.endDate.isNull)
                        .or(popupEntity.interestCategoryId.isNull)
                        .or(popupEntity.areaId.isNull),
                )
        val imminent = popupEntity.endDate.loe(criteria.imminentUntil)
        val refresh =
            popupEntity.endDate
                .goe(criteria.today)
                .and(
                    imminent
                        .and(popupEntity.enrichedAt.lt(criteria.imminentRefreshBefore))
                        .or(imminent.not().and(popupEntity.enrichedAt.lt(criteria.refreshBefore))),
                )

        return selectFrom(popupEntity)
            .where(unenriched.or(retry).or(refresh))
            .orderBy(popupEntity.enrichedAt.asc().nullsFirst(), popupEntity.id.asc())
            .limit(criteria.limit.toLong())
            .fetch()
    }

    override fun findImageTargets(
        today: LocalDate,
        retryBefore: OffsetDateTime,
        limit: Int,
    ): List<PopupEntity> =
        selectFrom(popupEntity)
            .where(
                // text[] 는 JPA 컬렉션이 아니라 IS EMPTY 를 못 쓴다. NULL · '{}' 모두 PostgreSQL cardinality 로 0 이 된다.
                Expressions
                    .numberTemplate(Int::class.javaObjectType, "coalesce(function('cardinality', {0}), 0)", popupEntity.imageUrls)
                    .eq(0),
                popupEntity.enrichedAt.isNotNull,
                popupEntity.brand.isNotNull.or(popupEntity.description.isNotNull),
                popupEntity.endDate.isNull.or(popupEntity.endDate.goe(today)),
                popupEntity.imageCheckedAt.isNull.or(popupEntity.imageCheckedAt.lt(retryBefore)),
            ).orderBy(popupEntity.id.asc())
            .limit(limit.toLong())
            .fetch()

    override fun findEmbedTargets(today: LocalDate): List<PopupEntity> =
        selectFrom(popupEntity)
            .where(popupEntity.endDate.isNull.or(popupEntity.endDate.goe(today)))
            .orderBy(popupEntity.id.asc())
            .fetch()
}
