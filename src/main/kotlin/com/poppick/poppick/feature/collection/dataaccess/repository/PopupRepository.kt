package com.poppick.poppick.feature.collection.dataaccess.repository

import com.poppick.poppick.feature.collection.domain.Popup
import com.poppick.poppick.feature.collection.domain.PopupCategory
import java.time.LocalDate

/**
 * 정규화 팝업 저장소. 상위 레이어는 이 인터페이스와 도메인 객체만 알고, JPA 구현은 [impl] 패키지에 격리한다.
 */
interface PopupRepository {
    fun save(popup: Popup): Popup

    fun saveAll(popups: List<Popup>): List<Popup>

    fun findById(popupId: Long): Popup?

    fun findByDedupeKey(dedupeKey: String): Popup?

    fun findAllByDedupeKeys(dedupeKeys: List<String>): List<Popup>

    fun existsByDedupeKey(dedupeKey: String): Boolean

    /** 종료일이 지나지 않은 진행 중 팝업. */
    fun findAllOngoing(baseDate: LocalDate): List<Popup>

    fun findAllOngoingBy(
        baseDate: LocalDate,
        areaCode: String,
        category: PopupCategory,
    ): List<Popup>

    /** 보강(Perplexity)이 한 번도 되지 않은 팝업. 반복 호출을 막기 위해 enrichedAt 기준으로 고른다. */
    fun findAllNotEnriched(limit: Int): List<Popup>
}
