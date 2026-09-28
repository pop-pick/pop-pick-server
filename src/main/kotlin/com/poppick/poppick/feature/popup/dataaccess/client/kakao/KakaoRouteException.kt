package com.poppick.poppick.feature.popup.dataaccess.client.kakao

/**
 * 카카오 도보 길찾기가 경로를 주지 못했다(status != OK) 또는 응답이 요청과 맞지 않는다.
 * 알려진 status: SAME_POINT · START_LINK_NOT_FOUND · END_LINK_NOT_FOUND · TOO_MANY_SEARCH_LINK · TOO_FAR_AWAY · ROUTE_RESULT_NOT_FOUND.
 */
class KakaoRouteException(
    message: String,
    /** 카카오 응답 status. 응답 불일치 등 status 와 무관한 실패면 NULL. */
    val status: String? = null,
) : RuntimeException(message)
