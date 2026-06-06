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

@RestController
@RequestMapping("/api/v1/data-recognition")
@Tag(name = "Data Recognition", description = "运营截图识别：OA 后端代理调用图片识别服务")
public class DataRecognitionController {
    private final RecognitionClient recognitionClient;

    public DataRecognitionController(RecognitionClient recognitionClient) {
        this.recognitionClient = recognitionClient;
    }

    @PostMapping(value = "/screenshots/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "识别运营数据截图", description = "上传小红书/抖音/视频号截图，由 OA 后端调用 tree-education-datacollecting 并返回图文或视频结构化字段。")
    public ApiResponse<RecognitionResponse> recognizeScreenshot(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "platform", defaultValue = "UNKNOWN") String platform,
            @RequestParam(value = "scene", defaultValue = "CONTENT_DETAIL") String scene,
            @RequestParam(value = "contentType", defaultValue = "AUTO") String contentType
    ) {
        return ApiResponse.ok(recognitionClient.recognize(file, platform, scene, contentType));
    }
}
