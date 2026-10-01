package com.poppick.poppick.feature.wish.presentation

import com.poppick.poppick.config.api.CursorableResolverConfig
import com.poppick.poppick.config.api.WebConfig
import com.poppick.poppick.config.security.SecurityConfig
import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.member.implement.MemberFinder
import com.poppick.poppick.feature.wish.WishFixtures
import com.poppick.poppick.feature.wish.WishFixtures.MEMBER_KEY
import com.poppick.poppick.feature.wish.business.WishService
import com.poppick.poppick.feature.wish.domain.WishedPopup
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.CursorableArgumentResolver
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.ZoneOffset

/** 실제 SecurityConfig(JWT 필터 · 인증 진입점)와 전역 예외 핸들러를 태운다. 서비스와 필터 의존성은 mockk. */
@WebMvcTest(WishController::class)
@Import(
    SecurityConfig::class,
    WebConfig::class,
    CursorableResolverConfig::class,
    CursorableArgumentResolver::class,
    JwtAuthenticationEntryPoint::class,
    WishControllerTest.MockBeans::class,
)
@ActiveProfiles("test")
class WishControllerTest {
    @TestConfiguration
    class MockBeans {
        @Bean
        fun wishService() = mockk<WishService>()

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
    lateinit var wishService: WishService

    private val member = Member.of(memberKey = MEMBER_KEY, email = "member@example.com", roles = setOf(MemberRole.ROLE_USER))

    @BeforeEach
    fun setUp() = clearMocks(wishService)

    private fun MockHttpServletRequestBuilder.asMember() =
        with(
            authentication(
                UsernamePasswordAuthenticationToken(AuthMember(member, emptyMap(), member.authorities), null, member.authorities),
            ),
        )

    @Test
    fun `PUT wish - 204`() {
        every { wishService.wish(MEMBER_KEY, 10) } returns Unit

        mockMvc.perform(put("/api/v1/popups/10/wish").asMember()).andExpect(status().isNoContent)

        verify(exactly = 1) { wishService.wish(MEMBER_KEY, 10) }
    }

    @Test
    fun `PUT wish - 없는 팝업은 404 E404`() {
        every { wishService.wish(MEMBER_KEY, 999) } throws AppException(ErrorType.NOT_FOUND_DATA)

        mockMvc
            .perform(put("/api/v1/popups/999/wish").asMember())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.errorCode").value("E404"))
    }

    @Test
    fun `DELETE wish - 204`() {
        every { wishService.unwish(MEMBER_KEY, 10) } returns Unit

        mockMvc.perform(delete("/api/v1/popups/10/wish").asMember()).andExpect(status().isNoContent)

        verify(exactly = 1) { wishService.unwish(MEMBER_KEY, 10) }
    }

    @Test
    fun `찜 API 3개는 토큰 없으면 401(팝업 상세 GET permitAll 이 wish 경로를 열지 않는다)`() {
        mockMvc.perform(put("/api/v1/popups/10/wish")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/api/v1/popups/10/wish")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/wishes")).andExpect(status().isUnauthorized)
        verify(exactly = 0) { wishService.wish(any(), any()) }
        verify(exactly = 0) { wishService.unwish(any(), any()) }
        verify(exactly = 0) { wishService.findWishes(any(), any()) }
    }

    @Test
    fun `GET wishes - 항목 필드와 hasNext 일 때 nextCursor 는 마지막 찜 id`() {
        val cursorable = Cursorable(42L, 2)
        val today = LocalDate.of(2026, 10, 2)
        val ended = WishedPopup.of(WishFixtures.wish(41, 120), WishFixtures.popup(120), today)
        val endedPopup =
            WishedPopup.of(
                WishFixtures.wish(40, 7, createdAt = WishFixtures.WISHED_AT.withOffsetSameInstant(ZoneOffset.UTC)),
                WishFixtures.popup(7, endDate = today.minusDays(1)).copy(imageUrls = null),
                today,
            )
        every { wishService.findWishes(MEMBER_KEY, cursorable) } returns Slice(listOf(ended, endedPopup), cursorable, true)

        mockMvc
            .perform(get("/api/v1/wishes").param("cursor", "42").param("limit", "2").asMember())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content", hasSize<Any>(2)))
            .andExpect(jsonPath("$.data.content[0].popupId").value(120))
            .andExpect(jsonPath("$.data.content[0].imageUrl").value("https://img.example/120-1.jpg"))
            .andExpect(jsonPath("$.data.content[0].interestCategoryId").value(3))
            .andExpect(jsonPath("$.data.content[0].title").value("팝업 120"))
            .andExpect(jsonPath("$.data.content[0].startDate").value("2026-09-20"))
            .andExpect(jsonPath("$.data.content[0].endDate").value("2026-10-12"))
            .andExpect(jsonPath("$.data.content[0].reservationType").value("UNKNOWN"))
            .andExpect(jsonPath("$.data.content[0].ended").value(false))
            .andExpect(jsonPath("$.data.content[0].wishedAt").value("2026-10-02T13:40:00+09:00"))
            .andExpect(jsonPath("$.data.content[1].ended").value(true))
            .andExpect(jsonPath("$.data.content[1].imageUrl").isEmpty)
            // UTC 로 저장된 값도 KST 로 내린다.
            .andExpect(jsonPath("$.data.content[1].wishedAt").value("2026-10-02T13:40:00+09:00"))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.nextCursor").value("40"))
    }

    @Test
    fun `GET wishes - limit 기본 10, 마지막 페이지면 nextCursor null`() {
        val cursorable = Cursorable<Long>(null, 10)
        every { wishService.findWishes(MEMBER_KEY, cursorable) } returns
            Slice(listOf(WishedPopup.of(WishFixtures.wish(1, 10), WishFixtures.popup(10), LocalDate.of(2026, 10, 2))), cursorable, false)

        mockMvc
            .perform(get("/api/v1/wishes").asMember())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.nextCursor").isEmpty)
    }

    @Test
    fun `GET wishes - 숫자가 아닌 cursor 와 limit 51 은 400`() {
        mockMvc
            .perform(get("/api/v1/wishes").param("cursor", "abc").asMember())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.errorCode").value("E400"))
        mockMvc
            .perform(get("/api/v1/wishes").param("limit", "51").asMember())
            .andExpect(status().isBadRequest)
        verify(exactly = 0) { wishService.findWishes(any(), any()) }
    }
}
