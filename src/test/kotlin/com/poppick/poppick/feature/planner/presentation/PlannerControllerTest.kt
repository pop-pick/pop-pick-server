package com.poppick.poppick.feature.planner.presentation

import com.poppick.poppick.config.api.WebConfig
import com.poppick.poppick.config.security.SecurityConfig
import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.member.domain.FavoriteArea
import com.poppick.poppick.feature.member.domain.InterestCategory
import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.member.domain.PreferredActivity
import com.poppick.poppick.feature.member.implement.MemberFinder
import com.poppick.poppick.feature.planner.PlannerFixtures
import com.poppick.poppick.feature.planner.business.PlannerService
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannerDetail
import com.poppick.poppick.feature.planner.domain.PlannerForm
import com.poppick.poppick.feature.planner.domain.PlannerGenerateCommand
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.security.domain.AuthMember
import com.poppick.poppick.security.entrypoint.JwtAuthenticationEntryPoint
import com.poppick.poppick.security.enums.MemberRole
import com.poppick.poppick.security.jwt.JwtValidator
import com.poppick.poppick.security.session.AccessTokenBlacklist
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalTime

/** 실제 SecurityConfig(JWT 필터 · 인증 진입점)와 전역 예외 핸들러를 태운다. 서비스와 필터 의존성은 mockk. */
@WebMvcTest(PlannerController::class)
@Import(SecurityConfig::class, WebConfig::class, JwtAuthenticationEntryPoint::class, PlannerControllerTest.MockBeans::class)
@ActiveProfiles("test")
class PlannerControllerTest {
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

    private val member = Member.of(memberKey = "member-1", email = "member@example.com", roles = setOf(MemberRole.ROLE_USER))
    private val detail = PlannerDetail(PlannerFixtures.planner(), "성수")

    private val validBody =
        """
        {
          "areaId": 1,
          "visitDate": "2026-10-03",
          "startTime": "14:00",
          "accompanyType": "WITH_FRIEND",
          "durationType": "HALF_DAY",
          "interestCategoryIds": [1, 5],
          "preferredActivityIds": [2],
          "note": "향수 만들기"
        }
        """.trimIndent()

    @BeforeEach
    fun setUp() = clearMocks(plannerService)

    private fun MockHttpServletRequestBuilder.asMember() =
        with(
            authentication(
                UsernamePasswordAuthenticationToken(AuthMember(member, emptyMap(), member.authorities), null, member.authorities),
            ),
        )

    @Test
    fun `GET form - 기본값 · 선택지 · 범위`() {
        every { plannerService.form("member-1") } returns
            PlannerForm(
                defaultAreaId = 1,
                defaultInterestCategoryIds = listOf(1, 5),
                defaultPreferredActivityIds = listOf(2),
                areas = listOf(FavoriteArea(1, "성수"), FavoriteArea(2, "여의도")),
                interestCategories = listOf(InterestCategory(1, "캐릭터/IP")),
                preferredActivities = listOf(PreferredActivity(2, "사진 찍기")),
                visitDateMin = LocalDate.of(2026, 9, 28),
                visitDateMax = LocalDate.of(2026, 10, 28),
            )

        mockMvc
            .perform(get("/api/v1/planners/form").asMember())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultType").value("SUCCESS"))
            .andExpect(jsonPath("$.data.defaults.areaId").value(1))
            .andExpect(jsonPath("$.data.defaults.interestCategoryIds[1]").value(5))
            .andExpect(jsonPath("$.data.options.areas[1].name").value("여의도"))
            .andExpect(jsonPath("$.data.options.interestCategories[0].name").value("캐릭터/IP"))
            .andExpect(jsonPath("$.data.options.preferredActivities[0].id").value(2))
            .andExpect(jsonPath("$.data.options.accompanyTypes", hasSize<Any>(AccompanyType.entries.size)))
            .andExpect(jsonPath("$.data.options.accompanyTypes[?(@.code == 'WITH_FRIEND')].label").value("친구와"))
            .andExpect(jsonPath("$.data.options.durationTypes[0].code").value("SHORT"))
            .andExpect(jsonPath("$.data.options.durationTypes[0].description").value("팝업 2곳, 약 3시간"))
            .andExpect(jsonPath("$.data.options.durationTypes[1].description").value("팝업 3~5곳, 약 5시간"))
            .andExpect(jsonPath("$.data.visitDateRange.max").value("2026-10-28"))
            .andExpect(jsonPath("$.data.startTimeRange.min").value("08:00"))
            .andExpect(jsonPath("$.data.startTimeRange.max").value("20:00"))
    }

    @Test
    fun `POST generate - 201 과 PlannerResponse, nextPath 는 없다`() {
        val command =
            PlannerGenerateCommand(
                areaId = 1,
                visitDate = LocalDate.of(2026, 10, 3),
                startTime = LocalTime.of(14, 0),
                accompanyType = AccompanyType.WITH_FRIEND,
                durationType = DurationType.HALF_DAY,
                interestCategoryIds = listOf(1, 5),
                preferredActivityIds = listOf(2),
                note = "향수 만들기",
            )
        every { plannerService.generate("member-1", command) } returns detail

        mockMvc
            .perform(post("/api/v1/planners/generate").asMember().contentType(MediaType.APPLICATION_JSON).content(validBody))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.plannerId").value(12))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.area.id").value(1))
            .andExpect(jsonPath("$.data.area.name").value("성수"))
            .andExpect(jsonPath("$.data.accompanyType").value("WITH_FRIEND"))
            .andExpect(jsonPath("$.data.visitDate").value("2026-10-03"))
            .andExpect(jsonPath("$.data.startTime").value("14:00"))
            .andExpect(jsonPath("$.data.endTime").value("17:20"))
            .andExpect(jsonPath("$.data.totalMin").value(200))
            .andExpect(jsonPath("$.data.createdAt").value("2026-09-28T19:40:00+09:00"))
            .andExpect(jsonPath("$.data.stops", hasSize<Any>(3)))
            .andExpect(jsonPath("$.data.stops[0].visitAt").value("14:00"))
            .andExpect(jsonPath("$.data.stops[0].popupId").value(1701))
            .andExpect(jsonPath("$.data.stops[0].nextTravelMin").value(10))
            .andExpect(jsonPath("$.data.stops[0].nextPath").doesNotExist())
            .andExpect(jsonPath("$.data.stops[2].nextTravelMin").isEmpty)
    }

    @Test
    fun `POST generate - 필수 값 누락이면 400 INVALID_REQUEST 와 필드`() {
        mockMvc
            .perform(
                post("/api/v1/planners/generate")
                    .asMember()
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody.replace("\"areaId\": 1,", "")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.errorCode").value("E400"))
            .andExpect(jsonPath("$.error.data[0].field").value("areaId"))
        verify(exactly = 0) { plannerService.generate(any(), any()) }
    }

    @Test
    fun `POST generate - note 201자면 400`() {
        mockMvc
            .perform(
                post("/api/v1/planners/generate")
                    .asMember()
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody.replace("향수 만들기", "가".repeat(201))),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.data[0].field").value("note"))
    }

    @Test
    fun `POST confirm - SCHEDULED 로 응답`() {
        every { plannerService.confirm("member-1", 12) } returns
            PlannerDetail(PlannerFixtures.planner(status = PlannerStatus.SCHEDULED), "성수")

        mockMvc
            .perform(post("/api/v1/planners/12/confirm").asMember())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
    }

    @Test
    fun `GET id - PlannerResponse`() {
        every { plannerService.get("member-1", 12) } returns detail

        mockMvc
            .perform(get("/api/v1/planners/12").asMember())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.plannerId").value(12))
            .andExpect(jsonPath("$.data.stops[1].title").value("팝업 2"))
            .andExpect(jsonPath("$.data.stops[1].nextPath").doesNotExist())
    }

    @Test
    fun `미인증이면 4개 모두 401`() {
        listOf(
            get("/api/v1/planners/form"),
            post("/api/v1/planners/generate").contentType(MediaType.APPLICATION_JSON).content(validBody),
            post("/api/v1/planners/12/confirm"),
            get("/api/v1/planners/12"),
        ).forEach { request ->
            mockMvc
                .perform(request)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.errorCode").value("E1000"))
        }
        verify(exactly = 0) { plannerService.form(any()) }
    }
}
