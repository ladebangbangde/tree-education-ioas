package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.recognition.ImageRecognitionDtos;
import com.treeeducation.ioas.recognition.ImageRecognitionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-ops/assets")
public class DataOperationAssetRecognitionController {
    private final ImageRecognitionService imageRecognitionService;

    public DataOperationAssetRecognitionController(ImageRecognitionService imageRecognitionService) {
        this.imageRecognitionService = imageRecognitionService;
    }

    @PostMapping("/{assetId}/recognize-current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA','MEDIA','OPERATOR')")
    public ApiResponse<ImageRecognitionDtos.Response> recognizeCurrentAsset(@PathVariable Long assetId,
                                                                            @RequestParam(required = false) String platform,
                                                                            @RequestParam(required = false) String scene) {
        return ApiResponse.ok(imageRecognitionService.recognizeDataAsset(assetId, platform, scene));
    }
}
