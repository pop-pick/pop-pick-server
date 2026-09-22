package com.poppick.poppick.feature.member.presentation

import com.poppick.poppick.feature.member.business.OnboardingService
import com.poppick.poppick.feature.member.presentation.dto.response.FavoriteAreaResponse
import com.poppick.poppick.feature.member.presentation.dto.response.InterestCategoryResponse
import com.poppick.poppick.feature.member.presentation.dto.response.PreferredActivityResponse
import com.poppick.poppick.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Onboarding", description = "온보딩 정보 APIs")
@RestController
@RequestMapping("/api/v1/onboardings")
class OnboardingController(
    private val onboardingService: OnboardingService,
) {
    @Operation(summary = "자주 가는 지역", description = "자주 가는 지역 목록을 조회한다.")
    @GetMapping("/favorite-areas")
    fun findFavoriteAreas() =
        ResponseEntity.ok(
            ApiResponse.success(
                onboardingService.findFavoriteAreas().map { FavoriteAreaResponse.from(it) },
            ),
        )

    @Operation(summary = "관심 카테고리", description = "관심 카테고리 목록을 조회한다.")
    @GetMapping("/interest-categories")
    fun findInterestCategories() =
        ResponseEntity.ok(
            ApiResponse.success(
                onboardingService.findInterestCategories().map { InterestCategoryResponse.from(it) },
            ),
        )

    @Operation(summary = "선호 활동", description = "선호 활동 목록을 조회한다.")
    @GetMapping("/preferred-activities")
    fun findPreferredActivities() =
        ResponseEntity.ok(
            ApiResponse.success(
                onboardingService.findPreferredActivities().map { PreferredActivityResponse.from(it) },
            ),
        )
}
