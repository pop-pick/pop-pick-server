package com.poppick.poppick.feature.auth.domain

class OAuth(
    val provider: OAuthProvider,
    val account: String,
    val memberKey: String,
    val id: Long,
)
