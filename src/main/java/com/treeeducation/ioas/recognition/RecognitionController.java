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
    public ApiResponse<RecognizeAndSaveResponse> recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "UNKNOWN") String platform,
            @RequestParam(defaultValue = "CONTENT_DETAIL") String scene,
            @RequestParam(defaultValue = "AUTO") String contentType
    ) {
        RecognitionResponse response = recognitionClient.recognize(file, platform, scene, contentType);
        DataRecognitionRecord record = dataRecognitionService.savePendingReview(response);
        return ApiResponse.ok(new RecognizeAndSaveResponse(record.getId(), response));
    }

    @Operation(summary = "查询识别记录列表", description = "用于人工校验页，支持按 status 或 contentType 过滤。")
    @GetMapping("/records")
    public ApiResponse<PageResult<RecordSummary>> listRecords(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(dataRecognitionService.list(status, contentType, pageNum, pageSize));
    }

    @Operation(summary = "查询识别记录详情", description = "返回 OCR 原文、图文/视频结构化结果、人工修正结果。")
    @GetMapping("/records/{id}")
    public ApiResponse<RecordDetail> getRecord(@PathVariable Long id) {
        return ApiResponse.ok(dataRecognitionService.getDetail(id));
    }

    @Operation(summary = "确认识别结果", description = "人工校验通过，可传 correctedResult 作为人工修正后的最终 JSON。")
    @PatchMapping("/records/{id}/confirm")
    public ApiResponse<RecordDetail> confirm(@PathVariable Long id, @RequestBody(required = false) ConfirmRequest request) {
        return ApiResponse.ok(dataRecognitionService.confirm(id, request));
    }

    @Operation(summary = "驳回识别结果", description = "人工判断该识别结果不可用，记录驳回原因。")
    @PatchMapping("/records/{id}/reject")
    public ApiResponse<RecordDetail> reject(@PathVariable Long id, @RequestBody(required = false) RejectRequest request) {
        return ApiResponse.ok(dataRecognitionService.reject(id, request));
    }
}
