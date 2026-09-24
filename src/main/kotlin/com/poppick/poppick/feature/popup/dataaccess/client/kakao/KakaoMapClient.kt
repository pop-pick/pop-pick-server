package com.poppick.poppick.feature.popup.dataaccess.client.kakao

import com.poppick.poppick.feature.popup.dataaccess.client.snakeCase
import com.poppick.poppick.feature.popup.domain.KakaoPlace
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration

@Component
class KakaoMapClient(
    jsonMapper: JsonMapper,
    @Value($$"${kakao.map.base-url}")
    baseUrl: String,
    /** 검색 영역 사각형(좌하단 경도,위도,우상단 경도,위도). */
    @Value($$"${kakao.map.seoul-rect}")
    private val seoulRect: String,
    /** 검색어 하나당 최대 페이지 수(페이지당 15건). */
    @Value($$"${kakao.map.max-page}")
    private val maxPage: Int,
    /** 페이지 요청 사이 대기(ms). */
    @Value($$"${kakao.map.page-delay-ms}")
    private val pageDelayMs: Long,
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
            .baseUrl(baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
                    .apply { setReadTimeout(READ_TIMEOUT) },
            ).build()

    private val mapper = jsonMapper.snakeCase()

    /** 페이지 사이 대기. 테스트에서 교체한다. */
    internal var sleeper: (Long) -> Unit = { Thread.sleep(it) }

    /** 검색어 하나를 서울 영역으로 maxPage 까지(또는 is_end 까지) 조회한다. 실패 시 예외를 그대로 던진다. */
    fun searchAll(query: String): List<KakaoPlace> {
        val places = mutableListOf<KakaoPlace>()
        for (page in 1..maxPage) {
            if (page > 1) sleeper(pageDelayMs)

            val response = search(query, page)
            places += response.documents.map { mapper.convertValue(it, KakaoPlaceDocument::class.java).toDomain(it) }

            if (response.meta.isEnd) break
        }
        return places
    }

    private fun search(
        query: String,
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
                        .queryParam("rect", seoulRect)
                        .build()
                }.header(HttpHeaders.AUTHORIZATION, "KakaoAK $apiKey")
                .retrieve()
                .body<ByteArray>() ?: error("카카오 키워드 검색 응답이 비었습니다. query=$query page=$page")

        return mapper.readValue(body, KakaoKeywordSearchResponse::class.java)
    }
}
