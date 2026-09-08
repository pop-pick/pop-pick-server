package com.poppick.poppick.feature.collection.domain

/**
 * 임베딩을 만든 관점. 같은 팝업에 관점별로 여러 임베딩을 붙일 수 있다.
 */
enum class EmbeddingKind {
    /**
     * 팝업의 기본 성격을 담은 임베딩.
     * `제목 · 브랜드 · 카테고리 · 태그 · 지역 · 설명` 을 합쳐서 만든다.
     * 날짜 · 예약 · 요금은 의미 유사도에 기여하지 않고 SQL 필터로 처리하므로 넣지 않는다.
     */
    PROFILE,
}
