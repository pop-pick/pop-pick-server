package com.poppick.poppick.feature.popup.dataaccess.client.kakao

import com.poppick.poppick.config.properties.KakaoMapProperties
import com.poppick.poppick.feature.popup.dataaccess.client.snakeCase
import com.poppick.poppick.feature.popup.domain.GeoRect
import com.poppick.poppick.feature.popup.domain.KakaoPlace
import com.poppick.poppick.feature.popup.domain.KakaoSearchResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration

private val log = KotlinLogging.logger { }

@Component
class KakaoMapClient(
    jsonMapper: JsonMapper,
    private val properties: KakaoMapProperties,
    /** 로그인(KakaoAuthenticator)과 같은 REST API 키. kakao.map.* 밖의 키라 그쪽과 같은 방식으로 받는다. */
    @Value($$"${kakao.api-key}")
    private val apiKey: String,
) {
    companion object {
        private const val KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json"
        private const val PAGE_SIZE = 15
        private val CONNECT_TIMEOUT = Duration.ofSeconds(3)
        private val READ_TIMEOUT = Duration.ofSeconds(5)
    }

    /** 테스트에서 MockRestServiceServer 에 바인딩한 RestClient 로 교체한다. */
    internal var restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
                    .apply { setReadTimeout(READ_TIMEOUT) },
            ).build()

    private val mapper = jsonMapper.snakeCase()

    /** 요청 사이 대기. 테스트에서 교체한다. */
    internal var sleeper: (Long) -> Unit = { Thread.sleep(it) }

    /**
     * 검색어 하나를 서울 영역 전체에서 조회한다. 카카오는 호출당 최대 45건(15 × 3페이지)이고 최신순 정렬이 없어서,
     * 결과가 잘린 영역(total_count > pageable_count)은 4등분해 칸마다 다시 조회한다(최대 maxSplitDepth 단계).
     * - 요청마다 5xx · 429 · IO 예외는 1회 재시도한다.
     * - 루트 영역 1페이지가 재시도까지 실패하면 예외를 그대로 던진다(검색어 실패).
     * - 그 외 요청이 재시도까지 실패하면 WARN 후 그때까지 모은 결과를 partial 로 반환한다.
     * 경계에 걸친 장소는 여러 칸에서 나오므로 place id 로 중복을 제거해 반환한다.
     */
    fun searchAll(query: String): KakaoSearchResult {
        val search = Search(query)
        val root = GeoRect.parse(properties.seoulRect)
        val first = search.fetch(root, 1)
        try {
            search.collect(root, depth = 0, first = first)
        } catch (e: PartialFailure) {
            log.warn {
                "kakao: 검색 일부 실패, 모은 결과만 반환 query='$query' rect=${e.rect.toParam()} page=${e.page} " +
                    "${e.cause?.javaClass?.simpleName}: ${e.cause?.message}"
            }
            search.partial = true
        }
        return KakaoSearchResult(search.places.values.toList(), search.partial, search.truncated)
    }

    /** 검색어 하나의 진행 상태. */
    private inner class Search(
        val query: String,
    ) {
        val places = LinkedHashMap<String, KakaoPlace>()
        var partial = false
        var truncated = false
        private var requests = 0

        fun collect(
            rect: GeoRect,
            depth: Int,
            first: KakaoKeywordSearchResponse,
        ) {
            add(first)
            if (first.meta.isEnd) return

            val total = first.meta.totalCount
            val pageable = first.meta.pageableCount
            if (total != null && pageable != null) {
                if (total <= pageable) {
                    fetchRemaining(rect)
                    return
                }
                // 잘린 영역: 쪼갤 수 있으면 이 영역의 2 · 3페이지는 받지 않는다.
                if (depth < properties.maxSplitDepth) {
                    split(rect, depth)
                } else {
                    fetchRemaining(rect)
                    markTruncated(rect, total)
                }
                return
            }

            // total_count · pageable_count 를 못 받으면 maxPage 까지 받은 뒤 is_end 로 잘림을 판단한다.
            if (fetchRemaining(rect)) return
            if (depth < properties.maxSplitDepth) split(rect, depth) else markTruncated(rect, total)
        }

        private fun split(
            rect: GeoRect,
            depth: Int,
        ) {
            for (quarter in rect.quarters()) {
                collect(quarter, depth + 1, fetchOrPartial(quarter, 1))
            }
        }

        /** 2페이지부터 is_end 또는 maxPage 까지 받는다. 마지막으로 받은 페이지의 is_end 를 반환한다. */
        private fun fetchRemaining(rect: GeoRect): Boolean {
            for (page in 2..properties.maxPage) {
                val response = fetchOrPartial(rect, page)
                add(response)
                if (response.meta.isEnd) return true
            }
            return false
        }

        private fun markTruncated(
            rect: GeoRect,
            total: Int?,
        ) {
            truncated = true
            log.warn { "kakao: 분할 한도에서도 결과가 잘림 query='$query' rect=${rect.toParam()} total_count=$total" }
        }

        private fun add(response: KakaoKeywordSearchResponse) {
            response.documents.forEach { raw ->
                val place = mapper.convertValue(raw, KakaoPlaceDocument::class.java).toDomain(raw)
                places.putIfAbsent(place.id, place)
            }
        }

        private fun fetchOrPartial(
            rect: GeoRect,
            page: Int,
        ): KakaoKeywordSearchResponse =
            try {
                fetch(rect, page)
            } catch (e: Exception) {
                throw PartialFailure(rect, page, e)
            }

        /** 요청 1건. 재시도 대상이면 1회 다시 보낸다. 모든 요청(재시도 포함) 사이에 pageDelayMs 를 둔다. */
        fun fetch(
            rect: GeoRect,
            page: Int,
        ): KakaoKeywordSearchResponse =
            try {
                request(rect, page)
            } catch (e: Exception) {
                if (!e.isRetryable()) throw e
                log.debug { "kakao: 재시도 query='$query' rect=${rect.toParam()} page=$page ${e.javaClass.simpleName}" }
                request(rect, page)
            }

        private fun request(
            rect: GeoRect,
            page: Int,
        ): KakaoKeywordSearchResponse {
            if (requests++ > 0) sleeper(properties.pageDelayMs)
            return search(query, rect, page)
        }
    }

    /** 루트 1페이지 이후 요청의 최종 실패. searchAll 이 받아 partial 로 바꾼다. */
    private class PartialFailure(
        val rect: GeoRect,
        val page: Int,
        cause: Exception,
    ) : RuntimeException(cause)

    // 5xx · 429 · IO(타임아웃 포함)만 재시도한다. 그 외 4xx 는 요청 자체가 잘못된 것이라 다시 보내지 않는다.
    private fun Exception.isRetryable() =
        this is HttpServerErrorException ||
            (this is HttpClientErrorException && statusCode.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) ||
            this is ResourceAccessException

    private fun search(
        query: String,
        rect: GeoRect,
        page: Int,
    ): KakaoKeywordSearchResponse {
        val body =
            restClient
                .get()
                .uri {
                    it
                        .path(KEYWORD_SEARCH_PATH)
                        .queryParam("query", query)
                        .queryParam("size", PAGE_SIZE)
                        .queryParam("page", page)
                        .queryParam("rect", rect.toParam())
                        .build()
                }.header(HttpHeaders.AUTHORIZATION, "KakaoAK $apiKey")
                .retrieve()
                .body<ByteArray>() ?: error("카카오 키워드 검색 응답이 비었습니다. query=$query page=$page")

        return mapper.readValue(body, KakaoKeywordSearchResponse::class.java)
    }
}
