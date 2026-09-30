package com.poppick.poppick.feature.planner

import com.poppick.poppick.feature.planner.domain.CourseDraft
import com.poppick.poppick.feature.planner.domain.CourseStop
import com.poppick.poppick.feature.planner.implement.CourseRouter
import com.poppick.poppick.feature.planner.implement.VisitScheduler
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.util.KST
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalTime

/**
 * 실제 카카오 도보 길찾기로 경로 · 방문 시각을 만들어 출력한다(수동 검증용). 좌표는 하드코딩, DB 는 쓰지 않는다.
 * local 프로파일(KAKAO_API_KEY) 이 필요하다. 결과는 눈으로 확인한다.
 */
@Disabled("수동 실행 전용: 카카오 API 호출")
@SpringBootTest
@ActiveProfiles("local")
class PlannerRouteIntegrationTest {
    @Autowired
    lateinit var courseRouter: CourseRouter

    @Autowired
    lateinit var visitScheduler: VisitScheduler

    @Test
    fun printSeongsu() {
        print(
            stop(1, "오래오래 함께가게", 37.54385, 127.05183),
            stop(2, "텐먼스", 37.54298, 127.05708),
            stop(3, "온그리디언츠", 37.54100, 127.06119),
        )
    }

    /** 롯데백화점 잠실점 두 팝업(같은 좌표) + 롯데월드몰. 가운데 구간이 카카오 없이 0 으로 채워져야 한다. */
    @Test
    fun printJamsilSamePoint() {
        print(
            stop(1, "롯데백화점 잠실점 팝업 A", 37.5122, 127.0995),
            stop(2, "롯데백화점 잠실점 팝업 B", 37.5122, 127.0995),
            stop(3, "롯데월드몰 팝업", 37.5137, 127.1043),
        )
    }

    private fun stop(
        id: Long,
        title: String,
        lat: Double,
        lng: Double,
    ) = CourseStop(Popup(id = id, source = SourceType.KAKAO_MAP, title = title, latitude = lat, longitude = lng), 60, "확인용")

    private fun print(vararg stops: CourseStop) {
        val draft = CourseDraft("확인용 코스", "확인용", stops.toList())
        val course = visitScheduler.schedule(draft, courseRouter.route(draft), LocalDate.now(KST), LocalTime.of(14, 0))

        println("${course.startTime} ~ ${course.endTime} (${course.totalMin}분, 이동 ${course.totalTravelM}m)")
        course.stops.forEach {
            println(
                "[${it.visitOrder}] ${it.visitAt} ${it.popup.title} (${it.stayMin}분) → " +
                    "${it.nextTravelMin}분/${it.nextTravelM}m, path=${it.nextPath?.size}점",
            )
        }
    }
}
