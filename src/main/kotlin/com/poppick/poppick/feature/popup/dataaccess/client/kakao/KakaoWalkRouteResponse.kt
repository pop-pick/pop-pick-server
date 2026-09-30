package com.poppick.poppick.feature.popup.dataaccess.client.kakao

/** GET /v2/routing/walk 응답 중 필요한 부분만. 필드는 camelCase 다. */
data class KakaoWalkRouteResponse(
    val status: String? = null,
    val route: Route? = null,
) {
    data class Route(
        val properties: RouteProperties? = null,
        val legs: List<Leg> = emptyList(),
    )

    data class RouteProperties(
        /** m */
        val totalDistance: Int = 0,
        /** s */
        val totalTime: Int = 0,
    )

    data class Leg(
        val properties: LegProperties? = null,
        val steps: List<Step> = emptyList(),
    )

    data class LegProperties(
        val distance: Int = 0,
        val time: Int = 0,
    )

    data class Step(
        val path: Path? = null,
    )

    data class Path(
        /** [[x(경도), y(위도)], ...] */
        val points: List<List<Double>> = emptyList(),
    )

    companion object {
        const val STATUS_OK = "OK"
    }
}
