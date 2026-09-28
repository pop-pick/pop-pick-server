package com.poppick.poppick.feature.planner.presentation

import com.poppick.poppick.feature.planner.business.PlannerService
import com.poppick.poppick.feature.planner.presentation.dto.response.PlannerCalendarResponse
import com.poppick.poppick.feature.planner.presentation.dto.response.SharedPlannerResponse
import com.poppick.poppick.global.response.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 공유 링크로 보는 플래너(비로그인). SecurityConfig 에서 permitAll. */
@Tag(name = "Shared Planner", description = "공유된 플래너 APIs(비로그인)")
@RestController
@RequestMapping("/api/v1/shared-planners")
class SharedPlannerController(
    private val plannerService: PlannerService,
) {
    @GetMapping("/{token}")
    fun get(
        @PathVariable token: String,
    ): ResponseEntity<ApiResponse<SharedPlannerResponse>> =
        ResponseEntity.ok(ApiResponse.success(SharedPlannerResponse.from(plannerService.getShared(token))))

    @GetMapping("/{token}/calendar")
    fun calendar(
        @PathVariable token: String,
    ): ResponseEntity<ApiResponse<PlannerCalendarResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(
                PlannerCalendarResponse(
                    googleCalendarUrl = plannerService.sharedCalendar(token),
                    icsUrl = "/api/v1/shared-planners/$token/calendar.ics",
                ),
            ),
        )

    // 공유 페이지에서는 내부 id 를 드러내지 않도록 파일 이름에 id 를 넣지 않는다.
    @GetMapping("/{token}/calendar.ics")
    fun calendarIcs(
        @PathVariable token: String,
    ): ResponseEntity<ByteArray> = CalendarFileResponse.of(plannerService.sharedCalendarIcs(token), "poppick-planner.ics")
}
