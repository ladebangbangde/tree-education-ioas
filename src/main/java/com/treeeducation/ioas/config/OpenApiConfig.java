package com.treeeducation.ioas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata and JWT bearer scheme. */
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI ioasOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Tree Education IOAS API").version("v1").description("正式后端接口契约：媒体主题包、素材、线索、任务、报表与审计。"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
