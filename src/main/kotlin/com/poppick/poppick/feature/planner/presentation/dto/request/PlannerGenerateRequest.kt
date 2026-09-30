package com.poppick.poppick.feature.planner.presentation.dto.request

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannerGenerateCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.UniqueElements
import java.time.LocalDate
import java.time.LocalTime

/** 필수 값은 nullable + @NotNull 로 받아 누락을 Bean Validation 필드 오류로 돌려준다. */
data class PlannerGenerateRequest(
    @field:NotNull
    @field:Schema(description = "지역 id(/form 의 options.areas)", example = "1")
    val areaId: Int?,
    @field:NotNull
    @field:Schema(description = "방문일. 오늘(KST)~30일 뒤", example = "2026-10-03")
    val visitDate: LocalDate?,
    @field:NotNull
    @field:Schema(description = "시작 시각 HH:mm. 08:00~20:00, 오늘이면 지금부터 30분 뒤 이후", example = "14:00")
    val startTime: LocalTime?,
    @field:NotNull
    @field:Schema(description = "동행 유형", example = "WITH_FRIEND")
    val accompanyType: AccompanyType?,
    @field:NotNull
    @field:Schema(description = "소요시간 유형. 방문지 수 · 예산은 /form 의 durationTypes 설명", example = "HALF_DAY")
    val durationType: DurationType?,
    @field:UniqueElements
    @field:Schema(description = "관심 카테고리 id(중복 불가). 비우면 카테고리 조건 없음", example = "[1, 5]")
    val interestCategoryIds: List<Int> = emptyList(),
    @field:UniqueElements
    @field:Schema(description = "선호 활동 id(중복 불가)", example = "[2]")
    val preferredActivityIds: List<Int> = emptyList(),
    @field:Size(max = 200)
    @field:Schema(description = "추가 요청(최대 200자)", example = "향수 만들기 체험이 있으면 좋겠어요")
    val note: String? = null,
) {
    fun toCommand() =
        PlannerGenerateCommand(
            areaId = areaId!!,
            visitDate = visitDate!!,
            startTime = startTime!!,
            accompanyType = accompanyType!!,
            durationType = durationType!!,
            interestCategoryIds = interestCategoryIds,
            preferredActivityIds = preferredActivityIds,
            note = note,
        )
}
