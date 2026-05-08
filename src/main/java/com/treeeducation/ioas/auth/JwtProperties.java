package com.treeeducation.ioas.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT configuration properties. */
@ConfigurationProperties(prefix = "ioas.jwt")
public record JwtProperties(String issuer, String secret, long expiresMinutes) {}
