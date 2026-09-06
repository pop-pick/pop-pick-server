package com.poppick.poppick.security.authenticator

import com.poppick.poppick.feature.auth.domain.OAuthLogin
import com.poppick.poppick.feature.auth.domain.OAuthProvider
import com.poppick.poppick.feature.auth.implement.OAuthAuthenticator
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.util.toMultiValueMap
import com.poppick.poppick.security.domain.KakaoAccessToken
import com.poppick.poppick.security.domain.KakaoAuthRequest
import com.poppick.poppick.security.domain.KakaoUser
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets.UTF_8

@Component
class KakaoAuthenticator(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    @Value($$"${kakao.api-key}")
    private val restApiKey: String,
) : OAuthAuthenticator {
    companion object {
        private const val KAKAO_TOKEN_REQUEST_URL = "https://kauth.kakao.com/oauth/token"
        private const val KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me"
    }

    override val provider = OAuthProvider.KAKAO

    override fun authenticate(oAuthLogin: OAuthLogin) =
        runCatching {
            fetchKakaoUser(authorize(oAuthLogin))
        }.getOrElse { throw AppException(ErrorType.INVALID_OAUTH_USER, cause = it) }
            .toOAuthMember()

    private fun authorize(oAuthLogin: OAuthLogin): KakaoAccessToken =
        runCatching {
            fetchAccessToken(oAuthLogin)
        }.getOrElse { throw AppException(ErrorType.FAILED_AUTH, cause = it) }

    private fun fetchKakaoUser(token: KakaoAccessToken): KakaoUser =
        restClient
            .get()
            .uri(KAKAO_USER_INFO_URL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${token.accessToken}")
            .retrieve()
            .body<KakaoUser>()!!

    private fun fetchAccessToken(oAuthLogin: OAuthLogin): KakaoAccessToken =
        restClient
            .post()
            .uri(KAKAO_TOKEN_REQUEST_URL)
            .contentType(MediaType(APPLICATION_FORM_URLENCODED, UTF_8))
            .body(
                KakaoAuthRequest(
                    clientId = restApiKey,
                    code = oAuthLogin.authToken,
                    redirectUri = oAuthLogin.redirectUri,
                ).toMultiValueMap(objectMapper),
            ).retrieve()
            .body<KakaoAccessToken>()!!
}
