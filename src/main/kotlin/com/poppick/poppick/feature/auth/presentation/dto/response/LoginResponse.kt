package com.poppick.poppick.feature.auth.presentation.dto.response

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
)
