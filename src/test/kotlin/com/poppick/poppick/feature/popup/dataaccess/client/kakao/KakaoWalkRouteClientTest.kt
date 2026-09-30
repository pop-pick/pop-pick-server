package com.poppick.poppick.feature.popup.dataaccess.client.kakao

import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.domain.GeoPoint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.hamcrest.Matchers.startsWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseActions
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class KakaoWalkRouteClientTest :
    FunSpec({
        // 오래오래 함께가게 → 텐먼스 → 온그리디언츠(성수)
        val seongsu =
            listOf(
                GeoPoint(lat = 37.54385, lng = 127.05183),
                GeoPoint(lat = 37.54298, lng = 127.05708),
                GeoPoint(lat = 37.54100, lng = 127.06119),
            )

        fun setUp(): Pair<KakaoWalkRouteClient, MockRestServiceServer> {
            val builder = RestClient.builder().baseUrl("https://dapi.kakao.com")
            val server = MockRestServiceServer.bindTo(builder).build()
            val client = KakaoWalkRouteClient(Fixtures.jsonMapper, Fixtures.kakaoMapProperties(), apiKey = "test-key")
            client.restClient = builder.build()
            return client to server
        }

        fun MockRestServiceServer.expectWalk(): ResponseActions =
            expect(requestTo(startsWith("https://dapi.kakao.com/v2/routing/walk")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))

        test("3점 요청: x = 경도, y = 위도, 가운데 점은 via") {
            val (client, server) = setUp()
            server
                .expectWalk()
                .andExpect(queryParam("start_x", "127.05183"))
                .andExpect(queryParam("start_y", "37.54385"))
                .andExpect(queryParam("via_x", "127.05708"))
                .andExpect(queryParam("via_y", "37.54298"))
                .andExpect(queryParam("end_x", "127.06119"))
                .andExpect(queryParam("end_y", "37.541"))
                .andRespond(withSuccess(Fixtures.read("kakao/walk-route-3points.json"), MediaType.APPLICATION_JSON))

            client.route(seongsu)

            server.verify()
        }

        test("5점 요청: via 3개를 콤마로 잇는다") {
            val (client, server) = setUp()
            val points = (0..4).map { GeoPoint(lat = 37.5 + it / 100.0, lng = 127.0 + it / 100.0) }
            val legs = (1..4).joinToString(",") { """{"properties": {"distance": 100, "time": 90}, "steps": []}""" }
            server
                .expectWalk()
                .andExpect(queryParam("via_x", "127.01,127.02,127.03"))
                .andExpect(queryParam("via_y", "37.51,37.52,37.53"))
                .andRespond(
                    withSuccess(
                        """{"status": "OK", "route": {"properties": {"totalDistance": 400, "totalTime": 360}, "legs": [$legs]}}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            client.route(points).legs.size shouldBe 4
            server.verify()
        }

        test("정상 응답을 WalkRoute 로 매핑한다: leg 별 거리 · 시간 · step 을 이은 path(연속 중복 제거)") {
            val (client, server) = setUp()
            server.expectWalk().andRespond(withSuccess(Fixtures.read("kakao/walk-route-3points.json"), MediaType.APPLICATION_JSON))

            val route = client.route(seongsu)

            route.totalDistanceM shouldBe 1100
            route.totalTimeSec shouldBe 990
            route.legs.map { it.distanceM } shouldBe listOf(480, 620)
            route.legs.map { it.timeSec } shouldBe listOf(432, 558)
            route.legs[0].path shouldBe
                listOf(
                    GeoPoint(lat = 37.54385, lng = 127.05183),
                    GeoPoint(lat = 37.5437, lng = 127.0530),
                    GeoPoint(lat = 37.54298, lng = 127.05708),
                )
            route.legs[1].path shouldBe listOf(GeoPoint(lat = 37.54298, lng = 127.05708), GeoPoint(lat = 37.54100, lng = 127.06119))
        }

        test("status 가 OK 가 아니면 KakaoRouteException(status)") {
            val (client, server) = setUp()
            server.expectWalk().andRespond(withSuccess("""{"status": "ROUTE_RESULT_NOT_FOUND"}""", MediaType.APPLICATION_JSON))

            shouldThrow<KakaoRouteException> { client.route(seongsu) }.status shouldBe "ROUTE_RESULT_NOT_FOUND"
        }

        test("legs 수가 지점 수 - 1 과 다르면 예외") {
            val (client, server) = setUp()
            server.expectWalk().andRespond(
                withSuccess(
                    """{"status": "OK", "route": {"properties": {"totalDistance": 1, "totalTime": 1}, "legs": [{"steps": []}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

            shouldThrow<KakaoRouteException> { client.route(seongsu) }.message!! shouldContain "legs mismatch"
        }
    })
