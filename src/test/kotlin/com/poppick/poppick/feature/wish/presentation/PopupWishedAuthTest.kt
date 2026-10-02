package com.poppick.poppick.feature.wish.presentation

import com.poppick.poppick.config.api.CursorableResolverConfig
import com.poppick.poppick.config.api.WebConfig
import com.poppick.poppick.config.security.SecurityConfig
import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.member.implement.MemberFinder
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popupdetail.business.PopupDetailService
import com.poppick.poppick.feature.popupdetail.domain.PopupDetail
import com.poppick.poppick.feature.popupdetail.presentation.PopupDetailController
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.feature.popuplist.domain.PopupListItem
import com.poppick.poppick.feature.popuplist.presentation.PopupListController
import com.poppick.poppick.feature.wish.WishFixtures
import com.poppick.poppick.feature.wish.WishFixtures.MEMBER_KEY
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.CursorableArgumentResolver
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.security.entrypoint.JwtAuthenticationEntryPoint
import com.poppick.poppick.security.enums.MemberRole
import com.poppick.poppick.security.enums.TokenType
import com.poppick.poppick.security.jwt.JwtValidator
import com.poppick.poppick.security.session.AccessTokenBlacklist
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * permitAll 인 팝업 목록 · 상세가 선택적 인증(@OptionalAuthMember)으로 wished 를 채우는지 확인한다.
 * 실제 SecurityConfig · JwtAuthenticationFilter 를 태우고, 토큰 검증 · 회원 조회만 mockk 로 대신한다.
 */
@WebMvcTest(PopupListController::class, PopupDetailController::class)
@Import(
    SecurityConfig::class,
    WebConfig::class,
    CursorableResolverConfig::class,
    CursorableArgumentResolver::class,
    JwtAuthenticationEntryPoint::class,
    PopupWishedAuthTest.MockBeans::class,
)
@ActiveProfiles("test")
class PopupWishedAuthTest {
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

    @Autowired
    lateinit var jwtValidator: JwtValidator

    @Autowired
    lateinit var memberFinder: MemberFinder

    @Autowired
    lateinit var accessTokenBlacklist: AccessTokenBlacklist

    private val member = Member.of(memberKey = MEMBER_KEY, email = "member@example.com", roles = setOf(MemberRole.ROLE_USER))
    private val cursorable = Cursorable<PopupSearchCursor>(null, 10)

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        // 유효한 액세스 토큰 "Bearer valid" → member-1
        every { jwtValidator.getBearerTokenBody("Bearer valid") } returns "valid"
        every { jwtValidator.validateTokenType("valid", TokenType.ACCESS) } just runs
        every { jwtValidator.getSubject("valid") } returns MEMBER_KEY
        every { jwtValidator.getJti("valid") } returns "jti-1"
        every { accessTokenBlacklist.contains("jti-1") } returns false
        every { memberFinder.find(MEMBER_KEY) } returns member
        // 목록 · 상세 응답의 카테고리 · 상권 이름(이 테스트의 관심사가 아니라 빈 맵)
        every { popupListService.findCategoryNames() } returns emptyMap()
        every { popupListService.findAreaNames() } returns emptyMap()
        every { popupDetailService.findCategoryNames() } returns emptyMap()
        every { popupDetailService.findAreaNames() } returns emptyMap()
    }

    private fun stubList(
        memberKey: String?,
        vararg items: Pair<Long, Boolean>,
    ) {
        every { popupListService.findPopups(memberKey, null, null, PopupSortType.LATEST, cursorable) } returns
            Slice(items.map { (id, wished) -> PopupListItem(WishFixtures.popup(id), wished) }, cursorable, false)
    }

    @Test
    fun `목록 - 토큰 없음은 200, 비로그인으로 조회해 wished = false`() {
        stubList(null, 2L to false, 1L to false)

        mockMvc
            .perform(get("/api/v1/popups"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].wished").value(false))
            .andExpect(jsonPath("$.data.content[1].wished").value(false))
    }

    @Test
    fun `목록 - 유효 토큰이면 회원 키로 조회해 찜한 항목만 wished = true`() {
        stubList(MEMBER_KEY, 2L to true, 1L to false)

        mockMvc
            .perform(get("/api/v1/popups").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].popupId").value(2))
            .andExpect(jsonPath("$.data.content[0].wished").value(true))
            .andExpect(jsonPath("$.data.content[1].wished").value(false))
    }

    @Test
    fun `상세 - 토큰 없음은 200, wished = false`() {
        every { popupDetailService.findPopupDetail(null, 1, any()) } returns PopupDetail(WishFixtures.popup(1), wished = false)

        mockMvc
            .perform(get("/api/v1/popups/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.wished").value(false))
    }

    @Test
    fun `상세 - 유효 토큰 + 찜함은 wished = true`() {
        every { popupDetailService.findPopupDetail(MEMBER_KEY, 1, any()) } returns PopupDetail(WishFixtures.popup(1), wished = true)

        mockMvc
            .perform(get("/api/v1/popups/1").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.wished").value(true))
    }

    @Test
    fun `상세 - 유효 토큰 + 안 찜함은 wished = false`() {
        every { popupDetailService.findPopupDetail(MEMBER_KEY, 1, any()) } returns PopupDetail(WishFixtures.popup(1), wished = false)

        mockMvc
            .perform(get("/api/v1/popups/1").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.wished").value(false))
    }
}
