package com.poppick.poppick.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** 카카오맵 로컬(키워드 검색) API 설정. 값은 전부 yml 의 kakao.map.* 에서 받는다. API 키는 로그인과 같은 kakao.api-key. */
@ConfigurationProperties("kakao.map")
data class KakaoMapProperties(
    val baseUrl: String,
    /** 검색 영역 사각형(좌하단 경도,위도,우상단 경도,위도). */
    val seoulRect: String,
    /** 검색어 하나당 최대 페이지 수(페이지당 15건). */
    val maxPage: Int,
    /** 요청 사이 대기(ms). 영역 분할 호출 · 재시도에도 똑같이 적용한다. */
    val pageDelayMs: Long,
    /** 결과가 잘린(total_count > pageable_count) 영역을 4등분해 다시 조회하는 최대 깊이. 0 이면 분할하지 않는다. */
    val maxSplitDepth: Int,
)
