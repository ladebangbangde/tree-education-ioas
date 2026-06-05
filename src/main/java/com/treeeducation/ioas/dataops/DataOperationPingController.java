package com.treeeducation.ioas.dataops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationPingController {
    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }
}
