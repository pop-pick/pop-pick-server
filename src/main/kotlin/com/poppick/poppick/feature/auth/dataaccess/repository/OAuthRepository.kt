package com.poppick.poppick.feature.auth.dataaccess.repository

import com.poppick.poppick.feature.auth.dataaccess.entity.OAuthEntity
import com.poppick.poppick.feature.auth.domain.OAuth
import com.poppick.poppick.feature.auth.domain.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository

interface OAuthRepository : JpaRepository<OAuthEntity, Long> {
    fun findByAccountAndProvider(account: String, provider: OAuthProvider): OAuth?
}
