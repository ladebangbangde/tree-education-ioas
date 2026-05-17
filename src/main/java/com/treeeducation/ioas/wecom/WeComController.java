package com.treeeducation.ioas.wecom;

import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Minimal WeCom integration demo controller. */
@RestController
@RequestMapping("/api/v1/wecom")
@EnableConfigurationProperties(WeComProperties.class)
@Tag(name = "WeCom", description = "企业微信 SCRM 集成 Demo")
public class WeComController {

    private final WeComProperties properties;
    private final RestClient restClient;

    public WeComController(WeComProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.apiBaseUrl()).build();
    }

    @GetMapping("/token")
    @Operation(summary = "获取企业微信 access_token")
    public ApiResponse<Map<?, ?>> token() {
        Map<?, ?> result = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/gettoken")
                        .queryParam("corpid", properties.corpId())
                        .queryParam("corpsecret", properties.secret())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);

        return ApiResponse.ok(result);
    }
}
