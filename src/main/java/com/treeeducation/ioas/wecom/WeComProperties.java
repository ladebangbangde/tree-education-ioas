package com.treeeducation.ioas.wecom;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Enterprise WeChat / WeCom integration configuration. */
@ConfigurationProperties(prefix = "ioas.wecom")
public record WeComProperties(
        boolean enabled,
        String corpId,
        String agentId,
        String secret,
        String callbackToken,
        String apiBaseUrl
) {
    public String apiBaseUrl() {
        return apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://qyapi.weixin.qq.com" : apiBaseUrl;
    }
}
