package com.poppick.poppick.feature.planner.presentation

import com.poppick.poppick.config.api.WebConfig
import com.poppick.poppick.config.security.SecurityConfig
import com.poppick.poppick.feature.member.implement.MemberFinder
import com.poppick.poppick.feature.planner.PlannerFixtures
import com.poppick.poppick.feature.planner.business.PlannerService
import com.poppick.poppick.feature.planner.domain.PlannerDetail
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.security.entrypoint.JwtAuthenticationEntryPoint
import com.poppick.poppick.security.jwt.JwtValidator
import com.poppick.poppick.security.session.AccessTokenBlacklist
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** 공유 조회는 비로그인(permitAll). 실제 SecurityConfig 를 태워 인증 없이 200 인지 본다. */
@WebMvcTest(SharedPlannerController::class)
@Import(SecurityConfig::class, WebConfig::class, JwtAuthenticationEntryPoint::class, SharedPlannerControllerTest.MockBeans::class)
@ActiveProfiles("test")
class SharedPlannerControllerTest {
    @TestConfiguration
    class MockBeans {
        @Bean
        fun plannerService() = mockk<PlannerService>()

        @Bean
        fun memberFinder() = mockk<MemberFinder>()

        @Bean
        fun jwtValidator() = mockk<JwtValidator>()

        @Bean
        fun accessTokenBlacklist() = mockk<AccessTokenBlacklist>()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var plannerService: PlannerService

    @BeforeEach
    fun setUp() = clearMocks(plannerService)

    @Test
    fun `미인증으로 200, plannerId · requestNote 는 없다`() {
        every { plannerService.getShared("k3Jx") } returns PlannerDetail(PlannerFixtures.planner(status = PlannerStatus.SCHEDULED), "성수")

        mockMvc
            .perform(get("/api/v1/shared-planners/k3Jx"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.title").value("성수 코스"))
            .andExpect(jsonPath("$.data.area.name").value("성수"))
            .andExpect(jsonPath("$.data.startTime").value("14:00"))
            .andExpect(jsonPath("$.data.stops[0].title").value("팝업 1"))
            .andExpect(jsonPath("$.data.plannerId").doesNotExist())
            .andExpect(jsonPath("$.data.requestNote").doesNotExist())
            .andExpect(jsonPath("$.data.memberKey").doesNotExist())
            .andExpect(jsonPath("$.data.stops[0].nextPath").doesNotExist())
    }

    @Test
    fun `없는 토큰은 404 PLANNER_NOT_FOUND`() {
        every { plannerService.getShared("nope") } throws AppException(ErrorType.PLANNER_NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/shared-planners/nope"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.errorCode").value("E3000"))
    }

    @Test
    fun `공유 캘린더도 미인증으로 200, ics 파일 이름에 id 가 없다`() {
        every { plannerService.sharedCalendar("k3Jx") } returns "https://calendar.google.com/calendar/render?action=TEMPLATE"
        every { plannerService.sharedCalendarIcs("k3Jx") } returns "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"

        mockMvc
            .perform(get("/api/v1/shared-planners/k3Jx/calendar"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.icsUrl").value("/api/v1/shared-planners/k3Jx/calendar.ics"))
        mockMvc
            .perform(get("/api/v1/shared-planners/k3Jx/calendar.ics"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/calendar;charset=UTF-8"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"poppick-planner.ics\""))
    }
}
