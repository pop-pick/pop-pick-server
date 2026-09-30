package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupEmbeddingRepository
import com.poppick.poppick.feature.popup.domain.PopupEmbedding
import com.poppick.poppick.feature.popup.domain.PopupSimilarity
import org.springframework.stereotype.Component
import java.time.LocalDate

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

    fun searchSimilar(
        queryVector: FloatArray?,
        model: String,
        areaId: Int,
        visitDate: LocalDate,
        excludePopupIds: Collection<Long>,
        limit: Int,
    ): List<PopupSimilarity> = popupEmbeddingRepository.searchSimilar(queryVector, model, areaId, visitDate, excludePopupIds, limit)
}
