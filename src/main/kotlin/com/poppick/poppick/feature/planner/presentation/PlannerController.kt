package com.poppick.poppick.feature.planner.presentation

import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.planner.business.PlannerService
import com.poppick.poppick.feature.planner.presentation.dto.request.PlannerGenerateRequest
import com.poppick.poppick.feature.planner.presentation.dto.response.PlannerFormResponse
import com.poppick.poppick.feature.planner.presentation.dto.response.PlannerResponse
import com.poppick.poppick.global.response.ApiResponse
import com.poppick.poppick.security.annotation.AuthMember
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Planner", description = "AI 플래너 APIs")
@RestController
@RequestMapping("/api/v1/planners")
class PlannerController(
    private val plannerService: PlannerService,
) {
    @GetMapping("/form")
    fun form(
        @AuthMember member: Member,
    ): ResponseEntity<ApiResponse<PlannerFormResponse>> =
        ResponseEntity.ok(ApiResponse.success(PlannerFormResponse.from(plannerService.form(member.memberKey))))

    @PostMapping("/generate")
    fun generate(
        @AuthMember member: Member,
        @Valid @RequestBody request: PlannerGenerateRequest,
    ): ResponseEntity<ApiResponse<PlannerResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(PlannerResponse.from(plannerService.generate(member.memberKey, request.toCommand()))))

    @PostMapping("/{plannerId}/confirm")
    fun confirm(
        @AuthMember member: Member,
        @PathVariable plannerId: Long,
    ): ResponseEntity<ApiResponse<PlannerResponse>> =
        ResponseEntity.ok(ApiResponse.success(PlannerResponse.from(plannerService.confirm(member.memberKey, plannerId))))

    @GetMapping("/{plannerId}")
    fun get(
        @AuthMember member: Member,
        @PathVariable plannerId: Long,
    ): ResponseEntity<ApiResponse<PlannerResponse>> =
        ResponseEntity.ok(ApiResponse.success(PlannerResponse.from(plannerService.get(member.memberKey, plannerId))))
}
