package com.treeeducation.ioas.media.assetfile;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** REST API for package-bound asset files. */
@RestController
@RequestMapping("/api/v1/media/assets")
@Tag(name = "Asset File", description = "素材查询、下载、预览、删除与回收站")
public class AssetFileController {
    private final AssetFileService service;

    public AssetFileController(AssetFileService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "素材文件列表")
    public ApiResponse<PageResponse<AssetFileDtos.Response>> list(@RequestParam(required = false) Long packageId,
                                                                  @RequestParam(defaultValue = "all") String fileType,
                                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(PageResponse.of(service.list(packageId, fileType), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "素材文件详情")
    public ApiResponse<AssetFileDtos.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(AssetFileDtos.of(service.get(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA')")
    @Operation(summary = "删除单文件到回收站")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        service.softDelete(id, p);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "下载素材文件")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        return service.download(id);
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "获取预览地址")
    public ApiResponse<AssetFileDtos.PreviewResponse> preview(@PathVariable Long id) {
        return ApiResponse.ok(service.preview(id));
    }

    @GetMapping("/recycle-bin")
    @Operation(summary = "文件回收站列表")
    public ApiResponse<PageResponse<AssetFileDtos.RecycleBinResponse>> recycle(@RequestParam(required = false) String keyword,
                                                                                @RequestParam(required = false) String fileType,
                                                                                @RequestParam(required = false) Long deletedBy,
                                                                                @RequestParam(required = false) Long packageId,
                                                                                @RequestParam(required = false) Long operatorId,
                                                                                @RequestParam(defaultValue = "1") int pageNum,
                                                                                @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(PageResponse.of(service.recycleBin(keyword, fileType, deletedBy, packageId, operatorId), pageNum, pageSize));
    }

    @PostMapping("/recycle-bin/{id}/restore")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA')")
    @Operation(summary = "恢复回收站文件")
    public ApiResponse<AssetFileDtos.Response> restore(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(AssetFileDtos.of(service.restore(id, p)));
    }

    @DeleteMapping("/recycle-bin/{id}/purge")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA')")
    @Operation(summary = "永久删除回收站文件")
    public ApiResponse<Void> purge(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        service.purge(id, p);
        return ApiResponse.ok();
    }
}
