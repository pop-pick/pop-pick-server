package com.poppick.poppick.feature.popup.dataaccess.client.web

import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/** 외부 페이지 요청 공통값. source_urls · 이미지 URL 은 외부 검색 결과라 임의 URL 로 취급한다. */
internal object WebUrls {
    const val USER_AGENT = "Mozilla/5.0 (compatible; PoppickBot/1.0)"
    const val MAX_REDIRECTS = 3

    /** 이미 인코딩된 URL 은 그대로, 한글 등 인코딩 안 된 문자가 있으면 인코딩해서 파싱한다. 실패하면 null. */
    fun parse(url: String): URI? =
        runCatching { URI(url.trim()) }
            .recoverCatching {
                UriComponentsBuilder
                    .fromUriString(url.trim())
                    .encode()
                    .build()
                    .toUri()
            }.getOrNull()

    /** https 이고 host 가 있으며 userinfo · 비표준 포트가 없는 URL 만 통과. */
    fun isSafeHttps(uri: URI) =
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443)
}
