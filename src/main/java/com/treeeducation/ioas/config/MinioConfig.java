package com.treeeducation.ioas.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${ioas.storage.endpoint}")
    private String endpoint;

    @Value("${ioas.storage.public-endpoint:${ioas.storage.endpoint}}")
    private String publicEndpoint;

    @Value("${ioas.storage.access-key}")
    private String accessKey;

    @Value("${ioas.storage.secret-key}")
    private String secretKey;

    @Bean
    @Qualifier("minioClient")
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    @Qualifier("publicMinioClient")
    public MinioClient publicMinioClient() {
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
