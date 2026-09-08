package com.poppick.poppick.feature.collection.dataaccess.repository

import com.poppick.poppick.feature.collection.domain.EmbeddingKind
import com.poppick.poppick.feature.collection.domain.PopupEmbedding

/**
 * 임베딩 저장소. (popupId, kind, model) 이 유일하며, contentHash 가 같으면 재임베딩을 생략한다.
 */
interface PopupEmbeddingRepository {
    fun save(popupEmbedding: PopupEmbedding): PopupEmbedding

    fun saveAll(popupEmbeddings: List<PopupEmbedding>): List<PopupEmbedding>

    fun findByPopupIdAndKindAndModel(
        popupId: Long,
        kind: EmbeddingKind,
        model: String,
    ): PopupEmbedding?

    fun findAllByPopupId(popupId: Long): List<PopupEmbedding>

    fun existsByPopupIdAndKindAndModelAndContentHash(
        popupId: Long,
        kind: EmbeddingKind,
        model: String,
        contentHash: String,
    ): Boolean
}
