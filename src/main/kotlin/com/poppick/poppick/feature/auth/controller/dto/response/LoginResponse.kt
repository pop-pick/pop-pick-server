package com.poppick.poppick.feature.auth.controller.dto.response

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
)
