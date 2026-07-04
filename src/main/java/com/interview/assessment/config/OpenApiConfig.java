package com.interview.assessment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Module 9: self-documenting API at /swagger-ui.html (springdoc-openapi-starter-webmvc-ui). */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI interviewAssessmentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Interview Assessment API")
                        .version("1.0.0")
                        .description("REST API for candidates, interview assessments, skill catalog, reporting and file uploads."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque-session-token")));
    }
}
