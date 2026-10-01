package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.member.domain.FavoriteArea
import com.poppick.poppick.feature.member.domain.InterestCategory
import com.poppick.poppick.feature.member.domain.PreferredActivity
import java.time.LocalDate

/** 생성 화면 초기값: 온보딩 기본값 + 선택지 + 입력 범위. */
data class PlannerForm(
    /** 온보딩 자주 가는 지역 중 첫 번째(id 오름차순). 온보딩 없으면 NULL. */
    val defaultAreaId: Int?,
    val defaultInterestCategoryIds: List<Int>,
    val defaultPreferredActivityIds: List<Int>,
    val areas: List<FavoriteArea>,
    val interestCategories: List<InterestCategory>,
    val preferredActivities: List<PreferredActivity>,
    val visitDateMin: LocalDate,
    val visitDateMax: LocalDate,
)

/** 조회 응답용: 플래너 + 지역 이름. */
data class PlannerDetail(
    val planner: Planner,
    /** favorite_area.area. 지역이 지워졌으면 NULL. */
    val areaName: String?,
)
