package com.poppick.poppick.config.api

import com.poppick.poppick.global.paging.CursorableArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CursorableResolverConfig(
    private val cursorableArgumentResolver: CursorableArgumentResolver,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(cursorableArgumentResolver)
    }
}
