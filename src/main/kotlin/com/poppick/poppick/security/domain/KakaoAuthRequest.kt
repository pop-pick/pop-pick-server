package com.poppick.poppick.security.domain

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoAuthRequest(
    val clientId: String,
    val redirectUri: String,
    val code: String,
    val grantType: String = "authorization_code",
)
