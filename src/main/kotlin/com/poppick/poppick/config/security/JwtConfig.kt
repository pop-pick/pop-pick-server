package com.poppick.poppick.config.security

import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.crypto.SecretKey
import kotlin.io.encoding.Base64

@Configuration
class JwtConfig {
    @Value($$"${jwt.secret-key}")
    private lateinit var secretKey: String

    @Bean
    fun key(): SecretKey = Keys.hmacShaKeyFor(Base64.decode(secretKey))
}