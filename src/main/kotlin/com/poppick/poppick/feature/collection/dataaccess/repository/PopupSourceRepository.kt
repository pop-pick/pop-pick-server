package com.poppick.poppick.feature.collection.dataaccess.repository

import com.poppick.poppick.feature.collection.domain.PopupSource
import com.poppick.poppick.feature.collection.domain.SourceType

/**
 * 원천 원본 저장소. 같은 원천의 같은 항목은 (source, externalId) 로 중복 저장하지 않는다.
 */
interface PopupSourceRepository {
    fun save(popupSource: PopupSource): PopupSource

    fun saveAll(popupSources: List<PopupSource>): List<PopupSource>

    fun findById(popupSourceId: Long): PopupSource?

    fun findBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): PopupSource?

    fun existsBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): Boolean

    fun findAllByPopupId(popupId: Long): List<PopupSource>

    /** 아직 정규화 팝업과 매칭되지 않은 원본. 정규화 단계의 입력이 된다. */
    fun findAllUnmatched(limit: Int): List<PopupSource>
}
