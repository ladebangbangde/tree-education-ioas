package com.treeeducation.ioas.recognition;

import com.treeeducation.ioas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/recognition")
@Tag(name = "ImageRecognition", description = "图片识别代理接口")
public class ImageRecognitionController {
    private final ImageRecognitionService service;

    public ImageRecognitionController(ImageRecognitionService service) {
        this.service = service;
    }

    @PostMapping("/social-metrics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','OPERATOR','MEDIA')")
    @Operation(summary = "临时上传图片并识别社媒数据")
    public ApiResponse<ImageRecognitionDtos.Response> socialMetrics(@RequestParam("file") MultipartFile file,
                                                                     @RequestParam String platform,
                                                                     @RequestParam(defaultValue = "CONTENT_DETAIL") String scene) {
        return ApiResponse.ok(service.recognizeUploaded(file, platform, scene));
    }
}
