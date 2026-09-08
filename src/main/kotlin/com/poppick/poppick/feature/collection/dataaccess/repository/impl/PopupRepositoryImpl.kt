package com.poppick.poppick.feature.collection.dataaccess.repository.impl

import com.poppick.poppick.feature.collection.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.collection.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.collection.dataaccess.repository.jpa.PopupJpaRepository
import com.poppick.poppick.feature.collection.domain.Popup
import com.poppick.poppick.feature.collection.domain.PopupCategory
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class PopupRepositoryImpl(
    private val popupJpaRepository: PopupJpaRepository,
) : PopupRepository {
    override fun save(popup: Popup): Popup = popupJpaRepository.save(PopupEntity.from(popup)).toDomain()

    override fun saveAll(popups: List<Popup>): List<Popup> =
        popupJpaRepository
            .saveAll(popups.map { PopupEntity.from(it) })
            .map { it.toDomain() }

    override fun findById(popupId: Long): Popup? = popupJpaRepository.findById(popupId).orElse(null)?.toDomain()

    override fun findByDedupeKey(dedupeKey: String): Popup? = popupJpaRepository.findByDedupeKey(dedupeKey)?.toDomain()

    override fun findAllByDedupeKeys(dedupeKeys: List<String>): List<Popup> =
        popupJpaRepository.findAllByDedupeKeyIn(dedupeKeys).map { it.toDomain() }

    override fun existsByDedupeKey(dedupeKey: String): Boolean = popupJpaRepository.existsByDedupeKey(dedupeKey)

    override fun findAllOngoing(baseDate: LocalDate): List<Popup> = popupJpaRepository.findAllOngoing(baseDate).map { it.toDomain() }

    override fun findAllOngoingBy(
        baseDate: LocalDate,
        areaCode: String,
        category: PopupCategory,
    ): List<Popup> = popupJpaRepository.findAllOngoingBy(baseDate, areaCode, category).map { it.toDomain() }

    override fun findAllNotEnriched(limit: Int): List<Popup> =
        popupJpaRepository.findAllByEnrichedAtIsNullOrderByIdAsc(Limit.of(limit)).map { it.toDomain() }
}
