package com.poppick.poppick.feature.collection.dataaccess.repository.jpa

import com.poppick.poppick.feature.collection.dataaccess.entity.PopupEmbeddingEntity
import com.poppick.poppick.feature.collection.domain.EmbeddingKind
import org.springframework.data.jpa.repository.JpaRepository

interface PopupEmbeddingJpaRepository : JpaRepository<PopupEmbeddingEntity, Long> {
    fun findByPopupIdAndKindAndModel(
        popupId: Long,
        kind: EmbeddingKind,
        model: String,
    ): PopupEmbeddingEntity?

    fun findAllByPopupId(popupId: Long): List<PopupEmbeddingEntity>

    fun existsByPopupIdAndKindAndModelAndContentHash(
        popupId: Long,
        kind: EmbeddingKind,
        model: String,
        contentHash: String,
    ): Boolean
}
