package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.planner.domain.CourseDraft
import com.poppick.poppick.feature.planner.domain.CourseStop
import com.poppick.poppick.feature.planner.domain.RouteFailedException
import com.poppick.poppick.feature.popup.dataaccess.client.kakao.KakaoRouteException
import com.poppick.poppick.feature.popup.dataaccess.client.kakao.KakaoWalkRouteClient
import com.poppick.poppick.feature.popup.domain.GeoPoint
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.RouteLeg
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.domain.WalkRoute
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class CourseRouterTest :
    FunSpec({
        val lotteDept = GeoPoint(lat = 37.5122, lng = 127.0995)
        val lotteDeptSameBuilding = GeoPoint(lat = 37.51222, lng = 127.09953)
        val lotteWorldMall = GeoPoint(lat = 37.5137, lng = 127.1043)
        val tenMonth = GeoPoint(lat = 37.54298, lng = 127.05708)

        fun draft(vararg points: GeoPoint) =
            CourseDraft(
                title = "코스",
                summary = "소개",
                stops =
                    points.mapIndexed { index, point ->
                        CourseStop(
                            popup =
                                Popup(
                                    id = index + 1L,
                                    source = SourceType.KAKAO_MAP,
                                    title = "팝업$index",
                                    latitude = point.lat,
                                    longitude = point.lng,
                                ),
                            stayMin = 60,
                            reason = "이유",
                        )
                    },
            )

        fun leg(distanceM: Int) = RouteLeg(distanceM = distanceM, timeSec = distanceM, path = listOf(lotteDept, lotteWorldMall))

        fun walkRoute(vararg legs: RouteLeg) = WalkRoute(legs.sumOf { it.distanceM }, legs.sumOf { it.timeSec }, legs.toList())

        val zero = RouteLeg(0, 0, emptyList())

        test("정상 3점 → 카카오 1회, 2구간") {
            val client = mockk<KakaoWalkRouteClient> { every { route(any()) } returns walkRoute(leg(500), leg(4000)) }

            val legs = CourseRouter(client).route(draft(lotteDept, lotteWorldMall, tenMonth))

            legs shouldBe listOf(leg(500), leg(4000))
            verify(exactly = 1) { client.route(listOf(lotteDept, lotteWorldMall, tenMonth)) }
        }

        test("2 · 3번째가 같은 좌표(20m 미만)면 카카오에 2점만 보내고 결과는 3구간(가운데 0)") {
            val client = mockk<KakaoWalkRouteClient> { every { route(any()) } returns walkRoute(leg(500), leg(4000)) }

            val legs = CourseRouter(client).route(draft(lotteWorldMall, lotteDept, lotteDeptSameBuilding, tenMonth))

            legs shouldBe listOf(leg(500), zero, leg(4000))
            verify(exactly = 1) { client.route(listOf(lotteWorldMall, lotteDept, tenMonth)) }
        }

        test("1 · 2번째가 같은 좌표면 첫 구간이 0") {
            val client = mockk<KakaoWalkRouteClient> { every { route(any()) } returns walkRoute(leg(500)) }

            val legs = CourseRouter(client).route(draft(lotteDept, lotteDeptSameBuilding, lotteWorldMall))

            legs shouldBe listOf(zero, leg(500))
            verify(exactly = 1) { client.route(listOf(lotteDept, lotteWorldMall)) }
        }

        test("전부 같은 좌표면 카카오 호출 없이 전부 0 구간") {
            val client = mockk<KakaoWalkRouteClient>()

            val legs = CourseRouter(client).route(draft(lotteDept, lotteDeptSameBuilding, lotteDept))

            legs shouldBe listOf(zero, zero)
            verify(exactly = 0) { client.route(any()) }
        }

        test("KakaoRouteException 은 RouteFailedException 으로 감싼다") {
            val client =
                mockk<KakaoWalkRouteClient> {
                    every { route(any()) } throws KakaoRouteException("카카오 도보 길찾기 실패", "ROUTE_RESULT_NOT_FOUND")
                }

            val e = shouldThrow<RouteFailedException> { CourseRouter(client).route(draft(lotteDept, tenMonth)) }

            e.cause.shouldBeInstanceOf<KakaoRouteException>()
            verify(exactly = 1) { client.route(any()) }
        }
    })
