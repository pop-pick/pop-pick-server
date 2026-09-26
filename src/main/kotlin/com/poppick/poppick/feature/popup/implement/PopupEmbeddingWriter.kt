package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEmbeddingEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupEmbeddingRepository
import com.poppick.poppick.feature.popup.domain.PopupEmbedding
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PopupEmbeddingWriter(
    private val popupEmbeddingRepository: PopupEmbeddingRepository,
) {
    /**
     * (popupId, kind, model) 기준 upsert(호출 1회 = 트랜잭션 1개).
     * 기존 행은 contentText · contentHash · embedding · model 을 갱신하고, 없으면 insert.
     */
    @Transactional
    fun upsert(embeddings: List<PopupEmbedding>) {
        embeddings.groupBy { it.kind to it.model }.forEach { (key, group) ->
            val (kind, model) = key
            val existing =
                popupEmbeddingRepository
                    .findAllByPopupIdInAndKindAndModel(group.map { it.popupId }, kind, model)
                    .associateBy { it.popupId }

            group.forEach { embedding ->
                val entity = existing[embedding.popupId]
                if (entity == null) {
                    popupEmbeddingRepository.save(PopupEmbeddingEntity.from(embedding))
                } else {
                    entity.contentText = embedding.contentText
                    entity.contentHash = embedding.contentHash
                    entity.embedding = embedding.embedding
                    entity.model = embedding.model
                }
            }
        }
    }
}
