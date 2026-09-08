package com.poppick.poppick.feature.collection.dataaccess.repository

import com.poppick.poppick.feature.collection.domain.SearchKeyword
import com.poppick.poppick.feature.collection.domain.SourceType

/**
 * 탐색 배치용 검색어 저장소. 배치는 [findAllActiveBy] 로 원천별 활성 검색어를 읽어 그대로 API 에 던진다.
 */
interface SearchKeywordRepository {
    /** 배치가 사용할 원천별 활성 검색어. 등록 순서(id)대로 돌려준다 */
    fun findAllActiveBy(targetSource: SourceType): List<SearchKeyword>

    /** 원천 구분 없이 활성 검색어 전체 */
    fun findAllActive(): List<SearchKeyword>

    /** 비활성 검색어까지 포함한 원천별 전체 목록. 운영 화면 · 점검용 */
    fun findAllBy(targetSource: SourceType): List<SearchKeyword>

    fun findById(searchKeywordId: Long): SearchKeyword?

    fun findByTargetSourceAndKeyword(
        targetSource: SourceType,
        keyword: String,
    ): SearchKeyword?

    fun existsByTargetSourceAndKeyword(
        targetSource: SourceType,
        keyword: String,
    ): Boolean

    /** 원천별 활성 검색어 개수. 시드가 제대로 들어갔는지 확인할 때 쓴다 */
    fun countActiveBy(targetSource: SourceType): Long

    fun save(searchKeyword: SearchKeyword): SearchKeyword

    fun saveAll(searchKeywords: List<SearchKeyword>): List<SearchKeyword>
}
