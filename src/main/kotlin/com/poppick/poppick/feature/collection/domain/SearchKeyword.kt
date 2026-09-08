package com.poppick.poppick.feature.collection.domain

/**
 * 팝업 탐색 배치가 원천 API 에 던지는 검색어. 완성된 검색어 1건 = 1행이며, 배치는 활성 검색어만 읽어 그대로 호출한다.
 * 카카오맵은 "성수 팝업스토어" 같은 짧은 장소 검색어, Perplexity 는 자연어 질의가 들어온다.
 */
data class SearchKeyword(
    /** 원천 API 에 그대로 넣는 완성 검색어 */
    val keyword: String,
    /** 이 검색어를 사용하는 원천 */
    val targetSource: SourceType,
    /** false 면 배치에서 제외한다. 삭제 대신 비활성화로 이력을 남긴다 */
    val isActive: Boolean = true,
    /** 검색어 식별자. 아직 저장되지 않은 검색어는 null */
    val id: Long? = null,
) {
    companion object {
        /**
         * 검색어를 둘 수 있는 원천. `search_keyword.target_source` 의 CHECK 제약과 같은 목록이다.
         * 서울열린데이터는 키워드 검색이 아니라 전체 목록 조회라 여기 포함하지 않는다.
         */
        val TARGET_SOURCES = setOf(SourceType.KAKAO_MAP, SourceType.PERPLEXITY)
    }

    /** 배치 대상에서 제외한다. 행을 지우지 않으므로 언제 어떤 검색어를 썼는지 이력이 남는다. */
    fun deactivate(): SearchKeyword = copy(isActive = false)

    fun activate(): SearchKeyword = copy(isActive = true)
}
