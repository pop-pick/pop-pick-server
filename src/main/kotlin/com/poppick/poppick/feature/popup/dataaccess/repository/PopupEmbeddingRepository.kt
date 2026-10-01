package com.poppick.poppick.feature.popup.dataaccess.repository

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEmbeddingEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.custom.CustomPopupEmbeddingRepository
import org.springframework.data.jpa.repository.JpaRepository

interface PopupEmbeddingRepository :
    JpaRepository<PopupEmbeddingEntity, Long>,
    CustomPopupEmbeddingRepository {
    fun findByPopupIdAndKindAndModel(
        popupId: Long,
        kind: String,
        model: String,
    ): PopupEmbeddingEntity?

    fun findAllByPopupIdInAndKindAndModel(
        popupIds: Collection<Long>,
        kind: String,
        model: String,
    ): List<PopupEmbeddingEntity>
}
