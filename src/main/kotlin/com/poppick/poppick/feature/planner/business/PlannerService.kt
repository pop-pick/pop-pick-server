package com.poppick.poppick.feature.planner.business

import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.member.implement.MemberPreferenceReader
import com.poppick.poppick.feature.member.implement.PreferredActivityReader
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.feature.planner.domain.PlannerDetail
import com.poppick.poppick.feature.planner.domain.PlannerForm
import com.poppick.poppick.feature.planner.domain.PlannerGenerateCommand
import com.poppick.poppick.feature.planner.domain.PlannerPolicy
import com.poppick.poppick.global.util.KST
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

/** 플래너 API 진입점. 생성 · 확정 · 조회는 PlannerGenerator 에 맡기고 응답에 필요한 지역 이름을 붙인다. */
@Service
class PlannerService(
    private val plannerGenerator: PlannerGenerator,
    private val memberPreferenceReader: MemberPreferenceReader,
    private val favoriteAreaReader: FavoriteAreaReader,
    private val interestCategoryReader: InterestCategoryReader,
    private val preferredActivityReader: PreferredActivityReader,
    private val clock: Clock = Clock.system(KST),
) {
    fun form(memberKey: String): PlannerForm {
        val preference = memberPreferenceReader.find(memberKey)
        val today = LocalDate.now(clock)
        return PlannerForm(
            defaultAreaId = preference.favoriteAreaIds.firstOrNull(),
            defaultInterestCategoryIds = preference.interestCategoryIds,
            defaultPreferredActivityIds = preference.preferredActivityIds,
            areas = favoriteAreaReader.findAll().sortedBy { it.id },
            interestCategories = interestCategoryReader.findAll().sortedBy { it.id },
            preferredActivities = preferredActivityReader.findAll().sortedBy { it.id },
            visitDateMin = today,
            visitDateMax = today.plusDays(PlannerPolicy.MAX_DAYS_AHEAD),
        )
    }

    fun generate(
        memberKey: String,
        command: PlannerGenerateCommand,
    ) = detail(plannerGenerator.generate(memberKey, command))

    fun confirm(
        memberKey: String,
        plannerId: Long,
    ) = detail(plannerGenerator.confirm(memberKey, plannerId))

    fun get(
        memberKey: String,
        plannerId: Long,
    ) = detail(plannerGenerator.get(memberKey, plannerId))

    private fun detail(planner: Planner) =
        PlannerDetail(planner, favoriteAreaReader.findAll().firstOrNull { it.id == planner.areaId }?.area)
}
