package com.poppick.poppick.feature.auth.presentation

import com.poppick.poppick.feature.auth.business.OAuthService
import com.poppick.poppick.feature.auth.business.TokenService
import com.poppick.poppick.feature.auth.presentation.dto.request.LoginRequest
import com.poppick.poppick.feature.auth.presentation.dto.request.LogoutRequest
import com.poppick.poppick.feature.auth.presentation.dto.request.RefreshRequest
import com.poppick.poppick.feature.auth.presentation.dto.response.LoginResponse
import com.poppick.poppick.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "인가/인증 APIs")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val oAuthService: OAuthService,
    private val tokenService: TokenService,
) {
    @Operation(summary = "로그인", description = "OAuth2 로그인을 한다. KAKAO와 GOOGLE을 지원한다.")
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val jwt = oAuthService.login(request.toOAuthLogin())
        return ResponseEntity.ok(ApiResponse.success(LoginResponse(jwt.accessToken, jwt.refreshToken)))
    }

    @Operation(summary = "토큰 재발급", description = "refresh token으로 access/refresh token을 재발급한다.")
    @PostMapping("/refresh")
    fun refresh(
        @RequestBody request: RefreshRequest,
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val jwt = tokenService.refresh(request.refreshToken)
        return ResponseEntity.ok(ApiResponse.success(LoginResponse(jwt.accessToken, jwt.refreshToken)))
    }

    @Operation(summary = "로그아웃", description = "access token을 블랙리스트에 등록하고 refresh 세션을 무효화한다.")
    @PostMapping("/logout")
    fun logout(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorizationHeader: String,
        @RequestBody request: LogoutRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        tokenService.logout(authorizationHeader, request.refreshToken)
        return ResponseEntity.ok(ApiResponse.success())
    }
}
