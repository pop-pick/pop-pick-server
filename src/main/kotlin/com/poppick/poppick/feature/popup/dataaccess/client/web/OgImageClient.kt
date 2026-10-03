package com.poppick.poppick.feature.popup.dataaccess.client.web

import com.poppick.poppick.config.properties.CollectionProperties
import com.poppick.poppick.feature.popup.domain.PageMeta
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

private val log = KotlinLogging.logger { }

/**
 * 출처 페이지의 대표 이미지(og:image, 없으면 twitter:image)와 제목(og:title, 없으면 title)을 읽는다.
 * source_urls 는 외부 검색 결과라 임의 URL 이므로
 * - https 이면서 collection.image.allowed-sources(host 정확히 일치 + 경로 접두사)에 맞는 URL 만 요청한다.
 * - 리다이렉트는 자동으로 따라가지 않고, 다음 위치도 허용 목록에 맞을 때만 최대 3번 따라간다.
 * - connect 3s · read 5s, 본문은 앞 1MB 만 읽어 파싱한다(og 태그는 head 에 있다).
 * HTTP 오류 · HTML 아님은 null, 연결 실패 등은 예외를 그대로 던진다.
 */
@Component
class OgImageClient(
    collectionProperties: CollectionProperties,
) {
    companion object {
        const val MAX_BODY_BYTES = 1024 * 1024
        private val CONNECT_TIMEOUT = Duration.ofSeconds(3)
        private val READ_TIMEOUT = Duration.ofSeconds(5)
        private const val IMAGE_SELECTOR =
            "meta[property=og:image], meta[name=og:image], meta[name=twitter:image], meta[property=twitter:image]"
        private const val TITLE_SELECTOR = "meta[property=og:title], meta[name=og:title]"
    }

    private val allowedSources = collectionProperties.image.allowedSources

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

    fun isAllowed(url: String) = WebUrls.parse(url)?.let(::isAllowed) ?: false

    fun fetch(pageUrl: String): PageMeta? {
        var uri = WebUrls.parse(pageUrl)?.takeIf(::isAllowed) ?: return null
        repeat(WebUrls.MAX_REDIRECTS + 1) {
            when (val fetched = get(uri)) {
                is Fetched.Page -> return fetched.toPageMeta(uri)
                is Fetched.Redirect -> {
                    val next = fetched.location?.let { runCatching { uri.resolve(it) }.getOrNull() }
                    if (next == null || !isAllowed(next)) {
                        log.debug { "image: 허용 목록 밖 리다이렉트 무시 from=$uri to=${fetched.location}" }
                        return null
                    }
                    uri = next
                }
                null -> return null
            }
        }
        log.debug { "image: 리다이렉트 횟수 초과 url=$pageUrl" }
        return null
    }

    private fun isAllowed(uri: URI): Boolean {
        if (!WebUrls.isSafeHttps(uri)) return false
        val host = uri.host.lowercase()
        val path = uri.path?.takeIf { it.isNotEmpty() } ?: "/"
        return allowedSources.any { it.host.equals(host, ignoreCase = true) && path.startsWith(it.pathPrefix) }
    }

    private sealed interface Fetched {
        class Page(
            val body: ByteArray,
            val contentType: MediaType?,
        ) : Fetched

        class Redirect(
            val location: URI?,
        ) : Fetched
    }

    private fun get(uri: URI): Fetched? =
        restClient
            .get()
            .uri(uri)
            .header(HttpHeaders.USER_AGENT, WebUrls.USER_AGENT)
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
            .exchange { _, response ->
                val status = response.statusCode
                val contentType = runCatching { response.headers.contentType }.getOrNull()
                when {
                    status.is3xxRedirection -> Fetched.Redirect(runCatching { response.headers.location }.getOrNull())
                    !status.is2xxSuccessful -> null.also { log.debug { "image: 페이지 응답 status=${status.value()} url=$uri" } }
                    contentType != null && !contentType.isHtml() -> null
                    else -> Fetched.Page(response.body.readNBytes(MAX_BODY_BYTES), contentType)
                }
            }

    private fun MediaType.isHtml() = isCompatibleWith(MediaType.TEXT_HTML) || isCompatibleWith(MediaType.APPLICATION_XHTML_XML)

    private fun Fetched.Page.toPageMeta(uri: URI): PageMeta {
        val document = Jsoup.parse(ByteArrayInputStream(body), contentType?.charset?.name(), uri.toString())
        return PageMeta(imageUrl = document.imageUrl(), title = document.pageTitle())
    }

    // absUrl 이 페이지 URL 기준으로 상대 경로를 절대 URL 로 바꾼다. https 가 아니면 버린다.
    private fun Document.imageUrl() =
        select(IMAGE_SELECTOR)
            .asSequence()
            .map { it.absUrl("content").trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.takeIf { url -> WebUrls.parse(url)?.let(WebUrls::isSafeHttps) == true }

    private fun Document.pageTitle() =
        (selectFirst(TITLE_SELECTOR)?.attr("content")?.trim()?.takeIf { it.isNotEmpty() } ?: title().trim())
            .takeIf { it.isNotEmpty() }
}
