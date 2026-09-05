package com.poppick.poppick.security.domain

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoProfile(
    val nickname: String,
    val thumbnailImageUrl: String,
    val profileImageUrl: String,
    val isDefaultImage: Boolean,
    val isDefaultNickname: Boolean,
)
