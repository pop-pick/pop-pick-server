package com.poppick.poppick.feature.auth.domain

class OAuthLogin(
    val oAuthProvider: OAuthProvider,
    val authToken: String,
    val redirectUri: String,
) {
}