package com.poppick.poppick.config.api

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.AntPathMatcher

@Configuration
class WebConfig {
    @Bean
    fun antPathMatcher() = AntPathMatcher()
}
