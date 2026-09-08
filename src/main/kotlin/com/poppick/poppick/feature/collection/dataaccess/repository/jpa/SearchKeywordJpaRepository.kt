package com.poppick.poppick.feature.collection.dataaccess.repository.jpa

import com.poppick.poppick.feature.collection.dataaccess.entity.SearchKeywordEntity
import com.poppick.poppick.feature.collection.domain.SourceType
import org.springframework.data.jpa.repository.JpaRepository

interface SearchKeywordJpaRepository : JpaRepository<SearchKeywordEntity, Long> {
    fun findAllByTargetSourceAndActiveTrueOrderByIdAsc(targetSource: SourceType): List<SearchKeywordEntity>

    fun findAllByActiveTrueOrderByIdAsc(): List<SearchKeywordEntity>

    fun findAllByTargetSourceOrderByIdAsc(targetSource: SourceType): List<SearchKeywordEntity>

    fun findByTargetSourceAndKeyword(
        targetSource: SourceType,
        keyword: String,
    ): SearchKeywordEntity?

    fun existsByTargetSourceAndKeyword(
        targetSource: SourceType,
        keyword: String,
    ): Boolean

    fun countByTargetSourceAndActiveTrue(targetSource: SourceType): Long
}
