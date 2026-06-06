package com.treeeducation.ioas.recognition;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration for the internal screenshot recognition service. */
@Component
@ConfigurationProperties(prefix = "ioas.recognition")
public class RecognitionProperties {
    private boolean enabled = true;
    private String baseUrl = "http://tree-education-datacollecting:18083";
    private String recognizePath = "/api/v1/recognize";
    private String token = "dev-recognition-token";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRecognizePath() {
        return recognizePath;
    }

    public void setRecognizePath(String recognizePath) {
        this.recognizePath = recognizePath;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
