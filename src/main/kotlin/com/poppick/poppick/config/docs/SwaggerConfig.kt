package com.poppick.poppick.config.docs

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    @Bean
    fun customOpenApi(): OpenAPI {
        val securitySchemeName = "bearerAuth"

        return OpenAPI().apply {
            info = openApiInfo()
            components =
                Components().addSecuritySchemes(
                    securitySchemeName,
                    SecurityScheme().apply {
                        type = SecurityScheme.Type.HTTP
                        scheme = "bearer"
                        bearerFormat = "JWT"
                    },
                )
            addSecurityItem(SecurityRequirement().addList(securitySchemeName))
        }
    }

    private fun openApiInfo() =
        Info().apply {
            title = "PopPick Server API"
            description = "PopPick Server API 명세서"
            version = "1.0"
        }
}