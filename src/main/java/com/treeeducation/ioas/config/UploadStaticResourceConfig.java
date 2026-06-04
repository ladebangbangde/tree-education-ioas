package com.treeeducation.ioas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class UploadStaticResourceConfig implements WebMvcConfigurer {
    @Value("${app.upload.base-dir:/app/uploads}")
    private String uploadBaseDir;

    @Value("${app.upload.public-prefix:/uploads}")
    private String uploadPublicPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = uploadPublicPrefix.endsWith("/") ? uploadPublicPrefix + "**" : uploadPublicPrefix + "/**";
        String location = Path.of(uploadBaseDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(pattern)
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
