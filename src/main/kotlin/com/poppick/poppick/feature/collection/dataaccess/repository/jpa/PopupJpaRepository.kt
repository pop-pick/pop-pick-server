package com.poppick.poppick.feature.collection.dataaccess.repository.jpa

import com.poppick.poppick.feature.collection.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.collection.domain.PopupCategory
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface PopupJpaRepository : JpaRepository<PopupEntity, Long> {
    fun findByDedupeKey(dedupeKey: String): PopupEntity?

    fun findAllByDedupeKeyIn(dedupeKeys: Collection<String>): List<PopupEntity>

    fun existsByDedupeKey(dedupeKey: String): Boolean

    fun findAllByEnrichedAtIsNullOrderByIdAsc(limit: Limit): List<PopupEntity>

    @Query(
        """
        SELECT p FROM PopupEntity p
        WHERE (p.endDate IS NULL OR p.endDate >= :baseDate)
          AND (p.startDate IS NULL OR p.startDate <= :baseDate)
        ORDER BY p.endDate ASC
        """,
    )
    fun findAllOngoing(
        @Param("baseDate") baseDate: LocalDate,
    ): List<PopupEntity>

    @Query(
        """
        SELECT p FROM PopupEntity p
        WHERE (p.endDate IS NULL OR p.endDate >= :baseDate)
          AND (p.startDate IS NULL OR p.startDate <= :baseDate)
          AND p.areaCode = :areaCode
          AND p.category = :category
        ORDER BY p.endDate ASC
        """,
    )
    fun findAllOngoingBy(
        @Param("baseDate") baseDate: LocalDate,
        @Param("areaCode") areaCode: String,
        @Param("category") category: PopupCategory,
    ): List<PopupEntity>
}
