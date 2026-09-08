package com.poppick.poppick.feature.collection.dataaccess.repository.jpa

import com.poppick.poppick.feature.collection.dataaccess.entity.PopupSourceEntity
import com.poppick.poppick.feature.collection.domain.SourceType
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository

interface PopupSourceJpaRepository : JpaRepository<PopupSourceEntity, Long> {
    fun findBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): PopupSourceEntity?

    fun existsBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): Boolean

    fun findAllByPopupId(popupId: Long): List<PopupSourceEntity>

    fun findAllByPopupIdIsNullOrderByIdAsc(limit: Limit): List<PopupSourceEntity>
}
