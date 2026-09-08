package com.poppick.poppick.feature.collection.dataaccess.repository.impl

import com.poppick.poppick.feature.collection.dataaccess.entity.PopupEmbeddingEntity
import com.poppick.poppick.feature.collection.dataaccess.repository.PopupEmbeddingRepository
import com.poppick.poppick.feature.collection.dataaccess.repository.jpa.PopupEmbeddingJpaRepository
import com.poppick.poppick.feature.collection.domain.EmbeddingKind
import com.poppick.poppick.feature.collection.domain.PopupEmbedding
import org.springframework.stereotype.Repository

@Repository
class PopupEmbeddingRepositoryImpl(
    private val popupEmbeddingJpaRepository: PopupEmbeddingJpaRepository,
) : PopupEmbeddingRepository {
    override fun save(popupEmbedding: PopupEmbedding): PopupEmbedding =
        popupEmbeddingJpaRepository.save(PopupEmbeddingEntity.from(popupEmbedding)).toDomain()

    override fun saveAll(popupEmbeddings: List<PopupEmbedding>): List<PopupEmbedding> =
        popupEmbeddingJpaRepository
            .saveAll(popupEmbeddings.map { PopupEmbeddingEntity.from(it) })
            .map { it.toDomain() }

    override fun findByPopupIdAndKindAndModel(
        popupId: Long,
        kind: EmbeddingKind,
        model: String,
    ): PopupEmbedding? = popupEmbeddingJpaRepository.findByPopupIdAndKindAndModel(popupId, kind, model)?.toDomain()

    override fun findAllByPopupId(popupId: Long): List<PopupEmbedding> =
        popupEmbeddingJpaRepository.findAllByPopupId(popupId).map { it.toDomain() }

    override fun existsByPopupIdAndKindAndModelAndContentHash(
        popupId: Long,
        kind: EmbeddingKind,
        model: String,
        contentHash: String,
    ): Boolean = popupEmbeddingJpaRepository.existsByPopupIdAndKindAndModelAndContentHash(popupId, kind, model, contentHash)
}
