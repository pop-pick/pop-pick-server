package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupEmbeddingRepository
import com.poppick.poppick.feature.popup.domain.PopupEmbedding
import org.springframework.stereotype.Component

@Component
class PopupEmbeddingReader(
    private val popupEmbeddingRepository: PopupEmbeddingRepository,
) {
    fun findAll(
        popupIds: Collection<Long>,
        kind: String,
        model: String,
    ): List<PopupEmbedding> =
        if (popupIds.isEmpty()) {
            emptyList()
        } else {
            popupEmbeddingRepository.findAllByPopupIdInAndKindAndModel(popupIds, kind, model).map { it.toDomain() }
        }
}
