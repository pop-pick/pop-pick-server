package com.poppick.poppick.feature.collection.dataaccess.repository.impl

import com.poppick.poppick.feature.collection.dataaccess.entity.PopupSourceEntity
import com.poppick.poppick.feature.collection.dataaccess.repository.PopupSourceRepository
import com.poppick.poppick.feature.collection.dataaccess.repository.jpa.PopupSourceJpaRepository
import com.poppick.poppick.feature.collection.domain.PopupSource
import com.poppick.poppick.feature.collection.domain.SourceType
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Repository

@Repository
class PopupSourceRepositoryImpl(
    private val popupSourceJpaRepository: PopupSourceJpaRepository,
) : PopupSourceRepository {
    override fun save(popupSource: PopupSource): PopupSource = popupSourceJpaRepository.save(PopupSourceEntity.from(popupSource)).toDomain()

    override fun saveAll(popupSources: List<PopupSource>): List<PopupSource> =
        popupSourceJpaRepository
            .saveAll(popupSources.map { PopupSourceEntity.from(it) })
            .map { it.toDomain() }

    override fun findById(popupSourceId: Long): PopupSource? = popupSourceJpaRepository.findById(popupSourceId).orElse(null)?.toDomain()

    override fun findBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): PopupSource? = popupSourceJpaRepository.findBySourceAndExternalId(source, externalId)?.toDomain()

    override fun existsBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): Boolean = popupSourceJpaRepository.existsBySourceAndExternalId(source, externalId)

    override fun findAllByPopupId(popupId: Long): List<PopupSource> =
        popupSourceJpaRepository.findAllByPopupId(popupId).map { it.toDomain() }

    override fun findAllUnmatched(limit: Int): List<PopupSource> =
        popupSourceJpaRepository.findAllByPopupIdIsNullOrderByIdAsc(Limit.of(limit)).map { it.toDomain() }
}
