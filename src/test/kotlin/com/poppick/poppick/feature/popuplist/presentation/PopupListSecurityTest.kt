package com.poppick.poppick.feature.popuplist.presentation

import com.poppick.poppick.config.api.WebConfig
import com.poppick.poppick.config.security.SecurityConfig
import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.member.implement.MemberFinder
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popupdetail.business.PopupDetailService
import com.poppick.poppick.feature.popupdetail.presentation.PopupDetailController
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.global.paging.Slice
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * 실제 SecurityConfig(JWT 필터 · 인증 진입점)를 태워 팝업 API 의 접근 규칙을 확인한다.
 * /recommended 는 회원 전용이고, 나머지 팝업 조회(목록 · 인기 · 지도 · 상세)는 비회원도 조회할 수 있다. 서비스는 mockk.
 */
@WebMvcTest(PopupListController::class, PopupDetailController::class)
@Import(SecurityConfig::class, WebConfig::class, JwtAuthenticationEntryPoint::class, PopupListSecurityTest.MockBeans::class)
@ActiveProfiles("test")
class PopupListSecurityTest {
    @TestConfiguration
    class MockBeans {
        @Bean
        fun popupListService() = mockk<PopupListService>()

        @Bean
        fun popupDetailService() = mockk<PopupDetailService>()

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
    lateinit var popupListService: PopupListService

    @Autowired
    lateinit var popupDetailService: PopupDetailService

    private val member = Member.of(memberKey = "member-1", email = "member@example.com", roles = setOf(MemberRole.ROLE_USER))

    private val popups =
        listOf(
            Popup(
                id = 1720,
                source = SourceType.KAKAO_MAP,
                title = "팝업 1720",
                startDate = LocalDate.of(2026, 9, 25),
                viewCount = 10,
                interestCategoryId = 1,
            ),
            Popup(id = 1715, source = SourceType.KAKAO_MAP, title = "팝업 1715", viewCount = 7),
        )

    @BeforeEach
    fun setUp() {
        clearMocks(popupListService, popupDetailService)
        every { popupListService.findCategoryNames() } returns mapOf(1 to "캐릭터/IP")
    }

    private fun MockHttpServletRequestBuilder.asMember() =
        with(
            authentication(
                UsernamePasswordAuthenticationToken(AuthMember(member, emptyMap(), member.authorities), null, member.authorities),
            ),
        )

    @Test
    fun `비회원이 recommended 를 호출하면 401 · E1000 이고 조회하지 않는다`() {
        mockMvc
            .perform(get("/api/v1/popups/recommended"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.errorCode").value("E1000"))

        verify(exactly = 0) { popupListService.findRecommendedPopups(any()) }
        verify(exactly = 0) { popupDetailService.findPopupDetail(any(), any()) }
    }

    @Test
    fun `회원이 recommended 를 호출하면 200 이고 memberKey 로 조회한 순서 그대로 목록 카드를 내려준다`() {
        every { popupListService.findRecommendedPopups("member-1") } returns popups

        mockMvc
            .perform(get("/api/v1/popups/recommended").asMember())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultType").value("SUCCESS"))
            .andExpect(jsonPath("$.data", hasSize<Any>(2)))
            .andExpect(jsonPath("$.data[0].popupId").value(1720))
            .andExpect(jsonPath("$.data[0].interestCategoryId").value(1))
            .andExpect(jsonPath("$.data[0].interestCategoryName").value("캐릭터/IP"))
            .andExpect(jsonPath("$.data[0].viewCount").doesNotExist())
            .andExpect(jsonPath("$.data[1].popupId").value(1715))
            .andExpect(jsonPath("$.data[1].interestCategoryName").doesNotExist())

        verify(exactly = 1) { popupListService.findRecommendedPopups("member-1") }
        verify(exactly = 1) { popupListService.findCategoryNames() }
        verify(exactly = 0) { popupDetailService.findPopupDetail(any(), any()) }
    }

    @Test
    fun `목록 · 인기 · 지도 · 상세는 기존처럼 비회원도 200`() {
        every { popupListService.findPopups(any(), any(), any()) } answers { Slice(popups, thirdArg(), false) }
        every { popupListService.findPopularPopups() } returns popups
        every { popupListService.findMapPopups(any(), any()) } returns listOf(popups[0].copy(latitude = 37.55, longitude = 127.0))
        every { popupListService.findAreaNames() } returns emptyMap()
        every { popupDetailService.findPopupDetail(1715L, any()) } returns popups[1]
        every { popupDetailService.findCategoryNames() } returns emptyMap()

        listOf(
            get("/api/v1/popups"),
            get("/api/v1/popups/popular"),
            get("/api/v1/popups/map?swLat=37.5&swLng=126.9&neLat=37.6&neLng=127.1"),
            get("/api/v1/popups/1715"),
        ).forEach { request ->
            mockMvc.perform(request).andExpect(status().isOk)
        }
        verify(exactly = 1) { popupDetailService.findPopupDetail(1715L, any()) }
        verify(exactly = 0) { popupListService.findRecommendedPopups(any()) }
    }
}
