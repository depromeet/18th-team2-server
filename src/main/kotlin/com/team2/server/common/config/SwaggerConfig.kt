package com.team2.server.common.config

import com.team2.server.common.swagger.OptionalAuth
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod
import java.lang.reflect.Method

@Configuration
@SecurityScheme(
    name = "Bearer Authentication",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
)
class SwaggerConfig {
    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Team2 API")
                .version("v1")
                .description("Team2 REST API"),
        )

    @Bean
    fun optionalAuthCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            if (!handlerMethod.hasOptionalAuth()) {
                return@OperationCustomizer operation
            }

            val security = operation.security ?: mutableListOf()
            if (security.none { it.isEmpty() }) {
                security.add(SecurityRequirement())
            }
            operation.security = security
            operation
        }

    private fun HandlerMethod.hasOptionalAuth(): Boolean =
        method.hasOptionalAuth() ||
            beanType.interfaces.any { type ->
                runCatching {
                    type.getMethod(method.name, *method.parameterTypes)
                }.getOrNull()?.hasOptionalAuth() == true
            }

    private fun Method.hasOptionalAuth(): Boolean = AnnotatedElementUtils.hasAnnotation(this, OptionalAuth::class.java)
}
