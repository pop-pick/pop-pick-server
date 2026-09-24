package com.poppick.poppick.feature.popup.dataaccess.repository

import com.poppick.poppick.feature.popup.dataaccess.entity.SearchKeywordEntity
import com.poppick.poppick.feature.popup.domain.SourceType
import org.springframework.data.jpa.repository.JpaRepository

interface SearchKeywordRepository : JpaRepository<SearchKeywordEntity, Long> {
    /** 해당 원천의 활성 검색어를 id 순으로 반환. 탐색 배치 진입점. */
    fun findAllByTargetSourceAndIsActiveTrueOrderByIdAsc(targetSource: SourceType): List<SearchKeywordEntity>
}
