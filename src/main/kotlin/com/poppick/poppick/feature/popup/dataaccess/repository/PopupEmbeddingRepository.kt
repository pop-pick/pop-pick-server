package com.poppick.poppick.feature.popup.dataaccess.repository

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEmbeddingEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface PopupEmbeddingRepository : JpaRepository<PopupEmbeddingEntity, Long> {
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

    /** JPQL bulk delete. 삭제 건수를 반환한다. */
    @Modifying
    @Query("DELETE FROM PopupEmbeddingEntity e WHERE e.popupId IN :popupIds")
    fun deleteByPopupIdIn(popupIds: Collection<Long>): Int
}
