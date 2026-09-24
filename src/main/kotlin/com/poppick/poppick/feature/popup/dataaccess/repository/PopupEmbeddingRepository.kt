package com.poppick.poppick.feature.popup.dataaccess.repository

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEmbeddingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PopupEmbeddingRepository : JpaRepository<PopupEmbeddingEntity, Long> {
    fun findByPopupIdAndKindAndModel(
        popupId: Long,
        kind: String,
        model: String,
    ): PopupEmbeddingEntity?
}
