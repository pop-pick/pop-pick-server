package com.poppick.poppick.feature.planner.presentation.dto.request

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannerGenerateCommand
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.UniqueElements
import java.time.LocalDate
import java.time.LocalTime

/** 필수 값은 nullable + @NotNull 로 받아 누락을 Bean Validation 필드 오류로 돌려준다. */
data class PlannerGenerateRequest(
    @field:NotNull
    val areaId: Int?,
    @field:NotNull
    val visitDate: LocalDate?,
    /** "HH:mm" */
    @field:NotNull
    val startTime: LocalTime?,
    @field:NotNull
    val accompanyType: AccompanyType?,
    @field:NotNull
    val durationType: DurationType?,
    @field:UniqueElements
    val interestCategoryIds: List<Int> = emptyList(),
    @field:UniqueElements
    val preferredActivityIds: List<Int> = emptyList(),
    @field:Size(max = 200)
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
