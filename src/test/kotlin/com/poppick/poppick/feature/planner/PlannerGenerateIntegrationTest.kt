package com.poppick.poppick.feature.planner

import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity
import com.poppick.poppick.feature.member.dataaccess.repository.MemberRepository
import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.member.implement.PreferredActivityReader
import com.poppick.poppick.feature.planner.business.PlannerGenerator
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannerGenerateCommand
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.util.KST
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * 실 DB · OpenAI · 카카오로 코스를 생성 · 저장하고 다시 읽어 출력한다(수동 검증용, 과금 소액). @Transactional 로 롤백된다.
 * local 프로파일(DB_* · OPENAI_API_KEY · KAKAO_API_KEY) 과 planner · planner_popup 테이블이 필요하다.
 */
@Disabled("수동 실행 전용: OpenAI · 카카오 과금, 실 DB 쓰기(롤백)")
@SpringBootTest
@ActiveProfiles("local")
class PlannerGenerateIntegrationTest {
    @Autowired
    lateinit var plannerGenerator: PlannerGenerator

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var interestCategoryReader: InterestCategoryReader

    @Autowired
    lateinit var preferredActivityReader: PreferredActivityReader

    @Test
    @Transactional
    fun generateSeongsu() {
        // member_key FK 를 걸어도 동작하도록 임시 회원을 만든다(롤백).
        val memberKey =
            memberRepository
                .saveAndFlush(
                    MemberEntity(memberKey = UUID.randomUUID().toString(), email = "planner-it@example.com"),
                ).memberKey
        val categoryIds = interestCategoryReader.findAll().filter { it.category in setOf("캐릭터/IP", "뷰티") }.map { it.id }
        val activityIds = preferredActivityReader.findAll().filter { it.activity == "사진 찍기" }.map { it.id }

        val planner =
            try {
                plannerGenerator.generate(
                    memberKey,
                    PlannerGenerateCommand(
                        areaId = 1,
                        visitDate = LocalDate.now(KST).plusDays(5),
                        startTime = LocalTime.of(14, 0),
                        accompanyType = AccompanyType.WITH_FRIEND,
                        durationType = DurationType.HALF_DAY,
                        interestCategoryIds = categoryIds,
                        preferredActivityIds = activityIds,
                        note = null,
                    ),
                )
            } catch (e: AppException) {
                assumeTrue(e.errorType != ErrorType.INSUFFICIENT_POPUPS, "후보 부족으로 건너뜀")
                throw e
            }

        val saved = plannerGenerator.get(memberKey, planner.id!!)
        println("[${saved.id}] ${saved.status} ${saved.title} / ${saved.summary}")
        println("${saved.visitDate} ${saved.startTime}~${saved.endTime} (${saved.totalMin}분, 이동 ${saved.totalTravelM}m)")
        saved.stops.forEach {
            println(
                "  [${it.visitOrder}] ${it.visitAt} ${it.popupId} ${it.title} (${it.stayMin}분) " +
                    "→ ${it.nextTravelMin}분/${it.nextTravelM}m path=${it.nextPath?.size}점 — ${it.reason}",
            )
        }
    }
}
