package com.poppick.poppick.feature.popup.domain

/** 팝업 레코드를 최초로 생성한 수집 원천. */
enum class SourceType {
    /** 카카오맵 장소 검색으로 수집. external_id 에 카카오 place id 가 들어간다. */
    KAKAO_MAP,

    /** Perplexity 웹 검색으로 수집. external_id 는 NULL. */
    PERPLEXITY,
}
