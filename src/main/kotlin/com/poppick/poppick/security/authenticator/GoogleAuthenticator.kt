package com.poppick.poppick.security.authenticator

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.feature.auth.domain.OAuthLogin
import com.poppick.poppick.feature.auth.domain.OAuthProvider
import com.poppick.poppick.feature.auth.implement.OAuthAuthenticator
import com.poppick.poppick.global.util.toMultiValueMap
import com.poppick.poppick.security.domain.GoogleAuthRequest
import com.poppick.poppick.security.domain.GoogleAccessToken
import com.poppick.poppick.security.domain.GoogleUser
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

@Component
class GoogleAuthenticator(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    @Value($$"${google.client-id}")
    private val clientId: String,
    @Value($$"${google.client-secret}")
    private val clientSecret: String,
) : OAuthAuthenticator {
    companion object {
        private const val GOOGLE_TOKEN_REQUEST_URL = "https://oauth2.googleapis.com/token"
        private const val GOOGLE_USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
    }

    override val provider = OAuthProvider.GOOGLE

    override fun authenticate(oAuthLogin: OAuthLogin) =
        runCatching {
            fetchGoogleUser(authorize(oAuthLogin))
        }.getOrElse { throw AppException(ErrorType.INVALID_OAUTH_USER, cause = it) }
            .toOAuthMember()

    private fun authorize(oAuthLogin: OAuthLogin): GoogleAccessToken = runCatching {
        fetchAccessToken(oAuthLogin)
    }.getOrElse { throw AppException(ErrorType.FAILED_AUTH, cause = it) }

    private fun fetchGoogleUser(token: GoogleAccessToken): GoogleUser = restClient
        .get()
        .uri(GOOGLE_USER_INFO_URL)
        .header(HttpHeaders.AUTHORIZATION, "Bearer ${token.accessToken}")
        .retrieve()
        .body<GoogleUser>()!!

    private fun fetchAccessToken(oAuthLogin: OAuthLogin): GoogleAccessToken = restClient
        .post()
        .uri(GOOGLE_TOKEN_REQUEST_URL)
        .contentType(MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8))
        .body(
            GoogleAuthRequest(
                clientId = clientId,
                clientSecret = clientSecret,
                code = oAuthLogin.authToken,
                redirectUri = oAuthLogin.redirectUri,
            ).toMultiValueMap(objectMapper)
        )
        .retrieve()
        .body<GoogleAccessToken>()!!
}
