package com.poppick.poppick.feature.collection.dataaccess.repository.impl

import com.poppick.poppick.feature.collection.dataaccess.entity.SearchKeywordEntity
import com.poppick.poppick.feature.collection.dataaccess.repository.SearchKeywordRepository
import com.poppick.poppick.feature.collection.dataaccess.repository.jpa.SearchKeywordJpaRepository
import com.poppick.poppick.feature.collection.domain.SearchKeyword
import com.poppick.poppick.feature.collection.domain.SourceType
import org.springframework.stereotype.Repository

@Repository
class SearchKeywordRepositoryImpl(
    private val searchKeywordJpaRepository: SearchKeywordJpaRepository,
) : SearchKeywordRepository {
    override fun findAllActiveBy(targetSource: SourceType): List<SearchKeyword> =
        searchKeywordJpaRepository.findAllByTargetSourceAndActiveTrueOrderByIdAsc(targetSource).map { it.toDomain() }

    override fun findAllActive(): List<SearchKeyword> = searchKeywordJpaRepository.findAllByActiveTrueOrderByIdAsc().map { it.toDomain() }

    override fun findAllBy(targetSource: SourceType): List<SearchKeyword> =
        searchKeywordJpaRepository.findAllByTargetSourceOrderByIdAsc(targetSource).map { it.toDomain() }

    override fun findById(searchKeywordId: Long): SearchKeyword? =
        searchKeywordJpaRepository.findById(searchKeywordId).orElse(null)?.toDomain()

    override fun findByTargetSourceAndKeyword(
        targetSource: SourceType,
        keyword: String,
    ): SearchKeyword? = searchKeywordJpaRepository.findByTargetSourceAndKeyword(targetSource, keyword)?.toDomain()

    override fun existsByTargetSourceAndKeyword(
        targetSource: SourceType,
        keyword: String,
    ): Boolean = searchKeywordJpaRepository.existsByTargetSourceAndKeyword(targetSource, keyword)

    override fun countActiveBy(targetSource: SourceType): Long = searchKeywordJpaRepository.countByTargetSourceAndActiveTrue(targetSource)

    override fun save(searchKeyword: SearchKeyword): SearchKeyword =
        searchKeywordJpaRepository.save(SearchKeywordEntity.from(searchKeyword)).toDomain()

    override fun saveAll(searchKeywords: List<SearchKeyword>): List<SearchKeyword> =
        searchKeywordJpaRepository
            .saveAll(searchKeywords.map { SearchKeywordEntity.from(it) })
            .map { it.toDomain() }
}
