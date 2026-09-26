package com.poppick.poppick.feature.member.presentation

import com.poppick.poppick.feature.member.business.MemberService
import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.member.presentation.dto.request.OnboardingRegisterRequest
import com.poppick.poppick.global.response.ApiResponse
import com.poppick.poppick.security.annotation.AuthMember
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Member", description = "유저 관련 APIs")
@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService,
) {
    @Operation(summary = "온보딩 정보 저장", description = "회원가입 한 유저의 온보딩 정보를 저장한다.")
    @PostMapping("/me/onboarding")
    fun saveOnboardingInfo(
        @AuthMember member: Member,
        @Valid @RequestBody request: OnboardingRegisterRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        memberService.registerOnBoardingInfo(request.toOnboardingInfo(member.memberKey))

        return ResponseEntity.ok(ApiResponse.success())
    }
}
