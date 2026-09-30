package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.planner.domain.CourseDraft
import com.poppick.poppick.feature.planner.domain.RouteFailedException
import com.poppick.poppick.feature.popup.dataaccess.client.kakao.KakaoRouteException
import com.poppick.poppick.feature.popup.dataaccess.client.kakao.KakaoWalkRouteClient
import com.poppick.poppick.feature.popup.domain.GeoDistance
import com.poppick.poppick.feature.popup.domain.GeoPoint
import com.poppick.poppick.feature.popup.domain.RouteLeg
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

private val log = KotlinLogging.logger { }

/**
 * 코스 stops 순서대로 도보 경로를 붙인다. 카카오는 1회만 호출한다.
 * 한 건물의 팝업 여러 개처럼 연속한 두 점이 20m 미만이면(카카오 SAME_POINT 회피) 카카오에 보내지 않고 0 구간으로 채운다.
 * 경로 없음은 재시도해도 같아서 재시도하지 않는다.
 */
@Component
class CourseRouter(
    private val kakaoWalkRouteClient: KakaoWalkRouteClient,
) {
    companion object {
        /** 이보다 가까운 연속 지점은 같은 곳으로 본다(m). */
        const val SAME_POINT_METERS = 20.0
        private val ZERO_LEG = RouteLeg(distanceM = 0, timeSec = 0, path = emptyList())
    }

    /** stops 수 - 1 개의 구간. */
    fun route(draft: CourseDraft): List<RouteLeg> {
        val points =
            draft.stops.map {
                GeoPoint(
                    lat = requireNotNull(it.popup.latitude) { "좌표 없는 팝업 popupId=${it.popup.id}" },
                    lng = requireNotNull(it.popup.longitude) { "좌표 없는 팝업 popupId=${it.popup.id}" },
                )
            }
        if (points.size < 2) return emptyList()

        // 직전에 남긴 점과 20m 이상 떨어진 점만 카카오에 보낸다.
        val kept = mutableListOf(0)
        for (i in 1 until points.size) {
            if (GeoDistance.meters(points[kept.last()], points[i]) >= SAME_POINT_METERS) kept += i
        }

        val kakaoLegs =
            if (kept.size < 2) {
                emptyList()
            } else {
                try {
                    kakaoWalkRouteClient.route(kept.map { points[it] }).legs
                } catch (e: KakaoRouteException) {
                    throw RouteFailedException("도보 경로 없음 status=${e.status}: ${e.message}", e)
                } catch (e: RestClientException) {
                    throw RouteFailedException("카카오 도보 길찾기 호출 실패: ${e.message}", e)
                }
            }

        // 원래 구간 i(점 i → i+1): i+1 이 남은 점이면 그 점에 도착하는 카카오 구간, 아니면 같은 곳이라 0.
        val keptPosition = kept.withIndex().associate { (position, index) -> index to position }
        val legs = (0 until points.size - 1).map { i -> keptPosition[i + 1]?.let { kakaoLegs[it - 1] } ?: ZERO_LEG }

        log.info {
            "planner route: stops=${points.size} legs=${legs.size} totalM=${legs.sumOf { it.distanceM }} " +
                "totalSec=${legs.sumOf { it.timeSec }} collapsed=${points.size - kept.size}"
        }
        return legs
    }
}
