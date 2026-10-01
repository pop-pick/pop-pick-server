package com.poppick.poppick.feature.popup.dataaccess.client.kakao

import com.poppick.poppick.config.properties.KakaoMapProperties
import com.poppick.poppick.feature.popup.domain.GeoPoint
import com.poppick.poppick.feature.popup.domain.PathSampler
import com.poppick.poppick.feature.popup.domain.RouteLeg
import com.poppick.poppick.feature.popup.domain.WalkRoute
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration

private val log = KotlinLogging.logger { }

/**
 * 카카오 도보 길찾기(GET /v2/routing/walk). 좌표는 WGS84, route_mode 는 기본값(BROAD_FIRST).
 * x = 경도(longitude), y = 위도(latitude). 뒤집어 보내면 카카오가 조용히 엉뚱한 경로나 START_LINK_NOT_FOUND 를 준다.
 * HTTP 4xx · 5xx · 타임아웃은 KakaoMapClient 와 같이 RestClient 예외를 그대로 던진다(재시도 없음).
 */
@Component
class KakaoWalkRouteClient(
    private val jsonMapper: JsonMapper,
    properties: KakaoMapProperties,
    /** 로그인 · 키워드 검색과 같은 REST API 키. */
    @Value($$"${kakao.api-key}")
    private val apiKey: String,
) {
    companion object {
        private const val WALK_ROUTE_PATH = "/v2/routing/walk"

        /** 카카오 경유지 한도. 출발 · 도착을 더해 최대 7점. */
        const val MAX_VIA = 5
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

    /** points 순서대로 걷는 경로. legs 는 (points 수 - 1) 개. */
    fun route(points: List<GeoPoint>): WalkRoute {
        require(points.size in 2..MAX_VIA + 2) { "지점 수 ${points.size} 는 2~${MAX_VIA + 2} 밖" }
        val start = points.first()
        val end = points.last()
        val vias = points.subList(1, points.size - 1)
        log.debug { "kakao walk 요청 points=${points.map { "${it.lng},${it.lat}" }}" }

        val body =
            restClient
                .get()
                .uri {
                    it
                        .path(WALK_ROUTE_PATH)
                        .queryParam("start_x", start.lng)
                        .queryParam("start_y", start.lat)
                        .apply {
                            if (vias.isNotEmpty()) {
                                queryParam("via_x", vias.joinToString(",") { via -> via.lng.toString() })
                                queryParam("via_y", vias.joinToString(",") { via -> via.lat.toString() })
                            }
                        }.queryParam("end_x", end.lng)
                        .queryParam("end_y", end.lat)
                        .build()
                }.header(HttpHeaders.AUTHORIZATION, "KakaoAK $apiKey")
                .retrieve()
                .body<ByteArray>() ?: throw KakaoRouteException("카카오 도보 길찾기 응답이 비었습니다.")

        val response = jsonMapper.readValue(body, KakaoWalkRouteResponse::class.java)
        log.debug { "kakao walk 응답 status=${response.status}" }

        val route = response.route
        if (response.status != KakaoWalkRouteResponse.STATUS_OK || route == null) {
            log.warn { "kakao walk 실패 status=${response.status} points=${points.size}" }
            throw KakaoRouteException("카카오 도보 길찾기 실패 status=${response.status}", response.status)
        }
        if (route.legs.size != points.size - 1) {
            log.warn { "kakao walk legs 불일치 expected=${points.size - 1} actual=${route.legs.size}" }
            throw KakaoRouteException("legs mismatch expected=${points.size - 1} actual=${route.legs.size}")
        }

        return WalkRoute(
            totalDistanceM = route.properties?.totalDistance ?: route.legs.sumOf { it.properties?.distance ?: 0 },
            totalTimeSec = route.properties?.totalTime ?: route.legs.sumOf { it.properties?.time ?: 0 },
            legs = route.legs.map { it.toDomain() },
        )
    }

    private fun KakaoWalkRouteResponse.Leg.toDomain() =
        RouteLeg(
            distanceM = properties?.distance ?: 0,
            timeSec = properties?.time ?: 0,
            path =
                PathSampler.sample(
                    steps
                        .flatMap { it.path?.points.orEmpty() }
                        .filter { it.size >= 2 }
                        .map { (x, y) -> GeoPoint(lat = y, lng = x) },
                ),
        )
}
