package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.domain.PopupSimilarity
import java.time.LocalDate

interface CustomPopupEmbeddingRepository {
    /**
     * 조건에 맞는 팝업을 쿼리 벡터와의 코사인 거리 오름차순으로 최대 limit 건.
     * queryVector 가 null 이면 벡터 정렬 없이 popup_id 내림차순(최근 수집 순).
     * - kind = PROFILE, model = :model
     * - p.area_id = :areaId
     * - (p.start_date IS NULL OR p.start_date <= :visitDate) AND (p.end_date IS NULL OR p.end_date >= :visitDate)
     *   기간이 비어 있으면 진행 중으로 본다(팝업 목록 · 검색 · 임베딩 대상과 같은 기준).
     * - p.latitude · p.longitude NOT NULL (길찾기에 좌표가 필요)
     * - p.popup_id NOT IN (:excludePopupIds)  — 비어 있으면 조건 생략
     */
    fun searchSimilar(
        queryVector: FloatArray?,
        model: String,
        areaId: Int,
        visitDate: LocalDate,
        excludePopupIds: Collection<Long>,
        limit: Int,
    ): List<PopupSimilarity>
}
