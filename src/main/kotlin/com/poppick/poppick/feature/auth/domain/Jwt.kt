package com.poppick.poppick.feature.auth.domain

import java.time.Instant

data class Jwt(
    val accessToken: String,
    val refreshToken: String,
    val sid: String,
    val refreshJti: String,
    val refreshExpiresAt: Instant,
)
