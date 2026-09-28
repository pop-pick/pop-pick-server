package com.poppick.poppick.feature.planner

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.CandidateCondition
import com.poppick.poppick.feature.planner.domain.CourseCondition
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.implement.CandidatePopupFinder
import com.poppick.poppick.feature.planner.implement.CourseComposer
import com.poppick.poppick.global.util.KST
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalTime

/**
 * 실데이터 후보 + 실제 OpenAI(임베딩 · Responses) 로 코스를 만들어 출력한다(수동 검증용, 과금 소액).
 * local 프로파일(DB_* · OPENAI_API_KEY) 이 필요하다. 결과는 눈으로 확인한다.
 */
@Disabled("수동 실행 전용: OpenAI 과금")
@SpringBootTest
@ActiveProfiles("local")
class PlannerCourseIntegrationTest {
    @Autowired
    lateinit var candidatePopupFinder: CandidatePopupFinder

    @Autowired
    lateinit var courseComposer: CourseComposer

    @Test
    fun printSeongsu() {
        val visitDate = LocalDate.now(KST).plusDays(5)
        val candidates =
            candidatePopupFinder.find(
                CandidateCondition(
                    areaId = 1,
                    visitDate = visitDate,
                    categories = listOf("캐릭터/IP", "뷰티"),
                    activities = listOf("사진 찍기"),
                    note = null,
                ),
            )
        val durationType = DurationType.HALF_DAY
        println("candidates=${candidates.size}")
        assumeTrue(candidates.size >= durationType.minStops, "후보 ${candidates.size}건 < minStops ${durationType.minStops}")

        val draft =
            courseComposer.compose(
                CourseCondition(
                    visitDate = visitDate,
                    startTime = LocalTime.of(14, 0),
                    accompanyType = AccompanyType.WITH_FRIEND,
                    durationType = durationType,
                    note = null,
                ),
                candidates,
            )

        println("${draft.title} / ${draft.summary}")
        draft.stops.forEachIndexed { index, stop ->
            println("[${index + 1}] ${stop.popup.id} ${stop.stayMin}분 ${stop.popup.title} — ${stop.reason}")
        }
    }
}
