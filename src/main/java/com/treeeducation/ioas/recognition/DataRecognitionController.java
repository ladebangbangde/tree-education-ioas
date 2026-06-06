package com.treeeducation.ioas.recognition;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.ConfirmRequest;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.PageResult;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.RecordDetail;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.RecordSummary;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.RecognizeAndSaveResponse;
import com.treeeducation.ioas.recognition.dto.DataRecognitionRecordDtos.RejectRequest;
import com.treeeducation.ioas.recognition.dto.RecognitionDtos.RecognitionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/data-recognition")
@Tag(name = "Data Recognition", description = "运营截图识别：OA 后端代理调用图片识别服务")
public class DataRecognitionController {
    private final RecognitionClient recognitionClient;
    private final DataRecognitionRecordService recordService;

    public DataRecognitionController(RecognitionClient recognitionClient, DataRecognitionRecordService recordService) {
        this.recognitionClient = recognitionClient;
        this.recordService = recordService;
    }

    @PostMapping(value = "/screenshots/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "识别运营数据截图", description = "只识别不入库，适合前端临时预览。")
    public ApiResponse<RecognitionResponse> recognizeScreenshot(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "platform", defaultValue = "UNKNOWN") String platform,
            @RequestParam(value = "scene", defaultValue = "CONTENT_DETAIL") String scene,
            @RequestParam(value = "contentType", defaultValue = "AUTO") String contentType
    ) {
        return ApiResponse.ok(recognitionClient.recognize(file, platform, scene, contentType));
    }

    @PostMapping(value = "/screenshots/recognize-and-save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "识别运营数据截图并保存", description = "识别后保存为 PENDING_REVIEW，供后台人工校验。")
    public ApiResponse<RecognizeAndSaveResponse> recognizeAndSaveScreenshot(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "platform", defaultValue = "UNKNOWN") String platform,
            @RequestParam(value = "scene", defaultValue = "CONTENT_DETAIL") String scene,
            @RequestParam(value = "contentType", defaultValue = "AUTO") String contentType
    ) {
        RecognitionResponse recognition = recognitionClient.recognize(file, platform, scene, contentType);
        DataRecognitionRecord record = recordService.savePending(recognition);
        return ApiResponse.ok(new RecognizeAndSaveResponse(record.getId(), recognition));
    }

    @GetMapping("/records")
    @Operation(summary = "查询识别记录列表", description = "支持按 status 或 contentType 过滤。")
    public ApiResponse<PageResult<RecordSummary>> listRecords(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(recordService.list(status, contentType, pageNum, pageSize));
    }

    @GetMapping("/records/{id}")
    @Operation(summary = "查询识别记录详情")
    public ApiResponse<RecordDetail> getRecord(@PathVariable Long id) {
        return ApiResponse.ok(recordService.getDetail(id));
    }

    @PatchMapping("/records/{id}/confirm")
    @Operation(summary = "确认识别结果", description = "人工校验通过，可传 correctedResult 保存修正后的最终结果。")
    public ApiResponse<RecordDetail> confirmRecord(@PathVariable Long id, @RequestBody(required = false) ConfirmRequest request) {
        return ApiResponse.ok(recordService.confirm(id, request));
    }

    @PatchMapping("/records/{id}/reject")
    @Operation(summary = "驳回识别结果")
    public ApiResponse<RecordDetail> rejectRecord(@PathVariable Long id, @RequestBody(required = false) RejectRequest request) {
        return ApiResponse.ok(recordService.reject(id, request));
    }
}
