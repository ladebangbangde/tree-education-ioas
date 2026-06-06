package com.treeeducation.ioas.recognition;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.recognition.dto.RecognitionDtos.RecognitionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Data Recognition", description = "运营截图识别：图文/视频数据 OCR 结构化")
@RestController
@RequestMapping("/api/v1/data-recognition")
public class RecognitionController {
    private final RecognitionClient recognitionClient;
    private final DataRecognitionService dataRecognitionService;

    public RecognitionController(RecognitionClient recognitionClient, DataRecognitionService dataRecognitionService) {
        this.recognitionClient = recognitionClient;
        this.dataRecognitionService = dataRecognitionService;
    }

    @Operation(summary = "上传运营截图并调用识别服务", description = "OA 前端上传截图到 IOAS，IOAS 再转发给 tree-education-datacollecting，返回图文/视频结构化识别结果，并保存为待人工校验记录。")
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RecognitionResponse> recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "UNKNOWN") String platform,
            @RequestParam(defaultValue = "CONTENT_DETAIL") String scene,
            @RequestParam(defaultValue = "AUTO") String contentType
    ) {
        RecognitionResponse response = recognitionClient.recognize(file, platform, scene, contentType);
        dataRecognitionService.savePendingReview(response);
        return ApiResponse.ok(response);
    }
}
