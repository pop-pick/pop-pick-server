package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.domain.PopupEmbedding
import com.poppick.poppick.feature.popup.domain.PopupSimilarity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.LocalDate

/**
 * QueryDSL 은 pgvector 연산자(`<=>`)를 못 쓰므로 네이티브 쿼리로 작성한다.
 * 벡터 인덱스는 두지 않는다: area · 날짜 pre-filter 후 남는 행이 수십 건이라 seq scan 으로 충분하고,
 * HNSW 는 pre-filter 와 조합하면 recall 이 떨어질 수 있어 데이터가 늘면 그때 판단한다.
 */
class CustomPopupEmbeddingRepositoryImpl : CustomPopupEmbeddingRepository {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun searchSimilar(
        queryVector: FloatArray?,
        model: String,
        areaId: Int,
        visitDate: LocalDate,
        excludePopupIds: Collection<Long>,
        limit: Int,
    ): List<PopupSimilarity> {
        val distance = if (queryVector != null) "e.embedding <=> CAST(:q AS vector)" else "CAST(NULL AS double precision)"
        val orderBy = if (queryVector != null) "distance ASC, e.popup_id DESC" else "e.popup_id DESC"
        val exclude = if (excludePopupIds.isNotEmpty()) "AND p.popup_id NOT IN (:exclude)" else ""
        val sql =
            """
            SELECT e.popup_id, $distance AS distance
            FROM popup_embedding e
            JOIN popup p ON p.popup_id = e.popup_id
            WHERE e.kind = :kind AND e.model = :model
              AND p.area_id = :areaId
              AND (p.start_date IS NULL OR p.start_date <= :visitDate)
              AND p.end_date >= :visitDate
              AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL
              $exclude
            ORDER BY $orderBy
            LIMIT :limit
            """.trimIndent()

        val query =
            entityManager
                .createNativeQuery(sql)
                .setParameter("kind", PopupEmbedding.KIND_PROFILE)
                .setParameter("model", model)
                .setParameter("areaId", areaId)
                .setParameter("visitDate", visitDate)
                .setParameter("limit", limit)
        // Hibernate 가 FloatArray 를 vector 파라미터로 직접 바인딩하지 못하는 경우가 있어 "[0.1,0.2,...]" 문자열로 넘긴다.
        queryVector?.let { query.setParameter("q", it.joinToString(",", "[", "]")) }
        if (excludePopupIds.isNotEmpty()) query.setParameter("exclude", excludePopupIds)

        return query.resultList.map { row ->
            val (popupId, distanceValue) = row as Array<*>
            PopupSimilarity(popupId = (popupId as Number).toLong(), distance = (distanceValue as Number?)?.toDouble())
        }
    }
}
