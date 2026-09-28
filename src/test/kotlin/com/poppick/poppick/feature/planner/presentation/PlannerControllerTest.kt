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
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import com.poppick.poppick.feature.planner.domain.PlannerShare
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerSummary
import com.poppick.poppick.feature.planner.domain.PlannerSummaryPage
import com.poppick.poppick.global.paging.Cursorable
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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
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

    @Test
    fun `GET 목록 - tab · cursor · size 를 넘기고 PageResponse(nextCursor) 로 응답`() {
        every { plannerService.list("member-1", PlannerListTab.PAST, Cursorable("2026-09-01T14:00_3", 10)) } returns
            PlannerSummaryPage(
                content =
                    listOf(
                        PlannerSummary(
                            id = 12,
                            status = PlannerStatus.SCHEDULED,
                            title = "성수 코스",
                            areaId = 1,
                            visitDate = LocalDate.of(2026, 8, 30),
                            startTime = LocalTime.of(14, 0),
                            endTime = LocalTime.of(17, 19),
                            totalMin = 199,
                            stopCount = 3,
                            firstStop = PlannerSummary.FirstStop("오래오래 함께가게", null),
                            canceledAt = null,
                            areaName = "성수",
                        ),
                    ),
                hasNext = true,
                nextCursor = "2026-08-30T14:00_12",
            )

        mockMvc
            .perform(
                get("/api/v1/planners")
                    .param("tab", "PAST")
                    .param("cursor", "2026-09-01T14:00_3")
                    .param("size", "10")
                    .asMember(),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.nextCursor").value("2026-08-30T14:00_12"))
            .andExpect(jsonPath("$.data.content[0].plannerId").value(12))
            .andExpect(jsonPath("$.data.content[0].area.name").value("성수"))
            .andExpect(jsonPath("$.data.content[0].startTime").value("14:00"))
            .andExpect(jsonPath("$.data.content[0].endTime").value("17:19"))
            .andExpect(jsonPath("$.data.content[0].stopCount").value(3))
            .andExpect(jsonPath("$.data.content[0].firstStop.title").value("오래오래 함께가게"))
            .andExpect(jsonPath("$.data.content[0].canceledAt").isEmpty)
            .andExpect(jsonPath("$.data.content[0].stops").doesNotExist())
    }

    @Test
    fun `GET 목록 - tab 기본값은 UPCOMING, size 기본 20`() {
        every { plannerService.list("member-1", PlannerListTab.UPCOMING, Cursorable(null, 20)) } returns
            PlannerSummaryPage(emptyList(), false, null)

        mockMvc
            .perform(get("/api/v1/planners").asMember())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content").isEmpty)
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `GET 목록 - 잘못된 tab 은 400, size 51 은 400`() {
        mockMvc
            .perform(get("/api/v1/planners").param("tab", "DRAFT").asMember())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.errorCode").value("E400"))
            .andExpect(jsonPath("$.error.data[0].field").value("tab"))
        mockMvc
            .perform(get("/api/v1/planners").param("size", "51").asMember())
            .andExpect(status().isBadRequest)
        verify(exactly = 0) { plannerService.list(any(), any(), any()) }
    }

    @Test
    fun `DELETE - 204`() {
        every { plannerService.cancel("member-1", 12) } returns Unit

        mockMvc
            .perform(delete("/api/v1/planners/12").asMember())
            .andExpect(status().isNoContent)
        verify { plannerService.cancel("member-1", 12) }
    }

    @Test
    fun `POST share - shareToken · shareUrl`() {
        every { plannerService.share("member-1", 12) } returns PlannerShare("k3Jx", "https://pop-pick.app/share/k3Jx")

        mockMvc
            .perform(post("/api/v1/planners/12/share").asMember())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.shareToken").value("k3Jx"))
            .andExpect(jsonPath("$.data.shareUrl").value("https://pop-pick.app/share/k3Jx"))
    }

    @Test
    fun `GET calendar - 구글 링크와 ics 경로`() {
        every { plannerService.calendar("member-1", 12) } returns "https://calendar.google.com/calendar/render?action=TEMPLATE"

        mockMvc
            .perform(get("/api/v1/planners/12/calendar").asMember())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.googleCalendarUrl").value("https://calendar.google.com/calendar/render?action=TEMPLATE"))
            .andExpect(jsonPath("$.data.icsUrl").value("/api/v1/planners/12/calendar.ics"))
    }

    @Test
    fun `GET calendar_ics - text calendar 와 첨부 파일 이름`() {
        every { plannerService.calendarIcs("member-1", 12) } returns "BEGIN:VCALENDAR\r\nSUMMARY:성수 코스\r\nEND:VCALENDAR\r\n"

        mockMvc
            .perform(get("/api/v1/planners/12/calendar.ics").asMember())
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/calendar;charset=UTF-8"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"poppick-planner-12.ics\""))
            .andExpect(content().bytes("BEGIN:VCALENDAR\r\nSUMMARY:성수 코스\r\nEND:VCALENDAR\r\n".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `6단계 엔드포인트도 미인증이면 401`() {
        listOf(
            get("/api/v1/planners"),
            delete("/api/v1/planners/12"),
            post("/api/v1/planners/12/share"),
            get("/api/v1/planners/12/calendar"),
            get("/api/v1/planners/12/calendar.ics"),
        ).forEach { request ->
            mockMvc.perform(request).andExpect(status().isUnauthorized)
        }
    }
}
