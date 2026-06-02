package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.recognition.ImageRecognitionDtos;
import com.treeeducation.ioas.recognition.ImageRecognitionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data-ops")
public class DataOperationRecognitionController {
    private final ImageRecognitionService recognitionService;

    public DataOperationRecognitionController(ImageRecognitionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    @PostMapping("/assets/{assetId}/recognize")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','MEDIA')")
    public ApiResponse<ImageRecognitionDtos.Response> recognizeAsset(@PathVariable Long assetId,
                                                                     @RequestParam(required = false) String platform,
                                                                     @RequestParam(required = false) String scene) {
        return ApiResponse.ok(recognitionService.recognizeDataAsset(assetId, platform, scene));
    }
}
