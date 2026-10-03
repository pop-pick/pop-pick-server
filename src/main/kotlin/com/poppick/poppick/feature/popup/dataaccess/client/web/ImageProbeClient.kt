package com.poppick.poppick.feature.popup.dataaccess.client.web

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

private val log = KotlinLogging.logger { }

/**
 * 이미지 URL 이 실제 이미지인지 확인한다(HEAD, 405 면 Range GET 1바이트). Content-Type 이 image/… 이면 true.
 * 이미지 호스트(CDN)는 허용 목록으로 묶을 수 없어서, 대신 https 만 요청하고 사설 · 루프백 주소로 풀리는 host 는 거른다.
 * 리다이렉트는 같은 검사를 거쳐 최대 3번 따라간다. 어떤 실패든 false(예외를 던지지 않는다).
 */
@Component
class ImageProbeClient {
    companion object {
        private val CONNECT_TIMEOUT = Duration.ofSeconds(3)
        private val READ_TIMEOUT = Duration.ofSeconds(5)
    }

    /** 테스트에서 MockRestServiceServer 에 바인딩한 RestClient 로 교체한다. */
    internal var restClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient
                        .newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                ).apply { setReadTimeout(READ_TIMEOUT) },
            ).build()

    /** host 의 주소 조회. 테스트에서 교체한다. */
    internal var resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }

    fun isImage(url: String): Boolean =
        runCatching { probe(url) }
            .onFailure { log.debug { "image: 검증 실패 url=$url ${it.javaClass.simpleName}: ${it.message}" } }
            .getOrDefault(false)

    private fun probe(url: String): Boolean {
        var uri = WebUrls.parse(url)?.takeIf(::isPublicHttps) ?: return false
        repeat(WebUrls.MAX_REDIRECTS + 1) {
            var probed = request(uri, HttpMethod.HEAD)
            if (probed.status == HttpStatus.METHOD_NOT_ALLOWED.value()) probed = request(uri, HttpMethod.GET)
            when {
                probed.status in 300..399 -> {
                    uri = probed.location?.let { uri.resolve(it) }?.takeIf(::isPublicHttps) ?: return false
                }
                probed.status in 200..299 -> return probed.contentType?.type.equals("image", ignoreCase = true)
                else -> return false
            }
        }
        return false
    }

    private fun isPublicHttps(uri: URI): Boolean {
        if (!WebUrls.isSafeHttps(uri)) return false
        val addresses = resolver(uri.host)
        return addresses.isNotEmpty() &&
            addresses.none {
                it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress
            }
    }

    private class Probed(
        val status: Int,
        val contentType: MediaType?,
        val location: URI?,
    )

    private fun request(
        uri: URI,
        method: HttpMethod,
    ): Probed =
        restClient
            .method(method)
            .uri(uri)
            .header(HttpHeaders.USER_AGENT, WebUrls.USER_AGENT)
            .apply { if (method == HttpMethod.GET) header(HttpHeaders.RANGE, "bytes=0-0") }
            .exchange { _, response ->
                Probed(
                    status = response.statusCode.value(),
                    contentType = runCatching { response.headers.contentType }.getOrNull(),
                    location = runCatching { response.headers.location }.getOrNull(),
                )
            }
}
