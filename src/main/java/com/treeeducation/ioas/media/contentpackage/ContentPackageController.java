package com.treeeducation.ioas.media.contentpackage;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.PageResponse;
import com.treeeducation.ioas.media.assetfile.AssetFileDtos;
import com.treeeducation.ioas.media.assetfile.AssetFileService;
import com.treeeducation.ioas.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** REST API for content packages. */
@RestController
@RequestMapping("/api/v1/media/content/packages")
@Tag(name = "Content Package", description = "媒体主题包管理")
public class ContentPackageController {
    private final ContentPackageService service;
    private final AssetFileService assetFileService;
    private final TaskService taskService;

    public ContentPackageController(ContentPackageService service, AssetFileService assetFileService, TaskService taskService) {
        this.service = service;
        this.assetFileService = assetFileService;
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "主题包列表")
    public ApiResponse<PageResponse<ContentPackageDtos.Response>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                                       @RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) Long operatorId,
                                                                       @RequestParam(required = false) ContentPackageStatus status,
                                                                       @RequestParam(required = false) String tab,
                                                                       @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(PageResponse.of(service.list(keyword, operatorId, status, tab, p), pageNum, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA')")
    @Operation(summary = "新建主题包；仅媒体或超管可创建")
    public ApiResponse<ContentPackageDtos.Response> create(@Valid @RequestBody ContentPackageDtos.UpsertRequest r,
                                                           @AuthenticationPrincipal UserPrincipal p) {
        try {
            return ApiResponse.ok(ContentPackageDtos.of(service.create(r, p)));
        } catch (RuntimeException ex) {
            taskService.createPackageCreateFailedTask(r == null ? null : r.topicName(), p == null ? null : p.id(), p == null ? null : p.userName(), ex.getMessage());
            throw ex;
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "主题包详情，包含按类型分组的素材")
    public ApiResponse<ContentPackageDtos.DetailResponse> get(@PathVariable Long id,
                                                              @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(service.detail(id, p));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA')")
    @Operation(summary = "编辑主题包运营人员与主题名称；仅媒体创建者或超管可编辑")
    public ApiResponse<ContentPackageDtos.Response> update(@PathVariable Long id, @Valid @RequestBody ContentPackageDtos.UpsertRequest r,
                                                            @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(ContentPackageDtos.of(service.update(id, r, p)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA')")
    @Operation(summary = "逻辑删除主题包，并将其下文件移入回收站；仅媒体创建者或超管可删除")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        service.delete(id, p);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/files")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA','OPERATOR')")
    @Operation(summary = "按 scripts/videos/images 分组批量上传素材；媒体创建者、绑定运营或超管可上传")
    public ApiResponse<AssetFileDtos.UploadSummary> uploadFiles(@PathVariable Long id,
                                                                @RequestPart(required = false) List<MultipartFile> scripts,
                                                                @RequestPart(required = false) List<MultipartFile> videos,
                                                                @RequestPart(required = false) List<MultipartFile> images,
                                                                @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(assetFileService.uploadGrouped(id, scripts, videos, images, p));
    }
}