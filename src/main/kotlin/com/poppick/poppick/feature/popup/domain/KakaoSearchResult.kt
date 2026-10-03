package com.poppick.poppick.feature.popup.domain

/** 검색어 하나의 카카오 키워드 검색 결과(place id 로 중복 제거됨). */
data class KakaoSearchResult(
    val places: List<KakaoPlace>,
    /** 루트 1페이지 이후의 요청(후속 페이지 · 분할 칸)이 재시도까지 실패해 그때까지 모은 결과만 담았는지. */
    val partial: Boolean = false,
    /** 분할 한도에서도 결과가 잘려 45건만 받은 영역이 있었는지. */
    val truncated: Boolean = false,
)
