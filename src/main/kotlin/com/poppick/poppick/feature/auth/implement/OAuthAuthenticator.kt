package com.poppick.poppick.feature.auth.implement

import com.poppick.poppick.feature.auth.domain.OAuthLogin
import com.poppick.poppick.feature.auth.domain.OAuthMember
import com.poppick.poppick.feature.auth.domain.OAuthProvider

interface OAuthAuthenticator {
    val provider: OAuthProvider

    fun authenticate(oAuthLogin: OAuthLogin): OAuthMember
}
