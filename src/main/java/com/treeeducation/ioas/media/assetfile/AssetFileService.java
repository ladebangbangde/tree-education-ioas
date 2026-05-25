package com.treeeducation.ioas.media.assetfile;

import com.treeeducation.ioas.audit.*;
import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.media.contentpackage.ContentPackageService;
import com.treeeducation.ioas.storage.ObjectStorageService;
import com.treeeducation.ioas.storage.StoredObject;
import com.treeeducation.ioas.task.TaskService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AssetFileService {
    private final AssetFileRepository repo;
    private final ContentPackageRepository packages;
    private final ContentPackageService packageService;
    private final ObjectStorageService storage;
    private final AuditLogRepository audits;
    private final TaskService tasks;

    public AssetFileService(AssetFileRepository repo, ContentPackageRepository packages, ContentPackageService packageService,
                            ObjectStorageService storage, AuditLogRepository audits, TaskService tasks) {
        this.repo = repo;
        this.packages = packages;
        this.packageService = packageService;
        this.storage = storage;
        this.audits = audits;
        this.tasks = tasks;
    }

    @Transactional
    public AssetFileDtos.UploadSummary uploadGrouped(Long packageId, List<MultipartFile> scripts, List<MultipartFile> videos,
                                                     List<MultipartFile> images, UserPrincipal p) {
        ContentPackage cp = packages.findById(packageId).orElseThrow(() -> BusinessException.notFound("主题包不存在"));
        packageService.assertCanUploadPackage(cp, p);
        tasks.ensureMediaUploadTask(cp);
        try {
            List<AssetFileDtos.Response> scriptResults = uploadMany(packageId, AssetFileType.script, scripts, p);
            List<AssetFileDtos.Response> videoResults = uploadMany(packageId, AssetFileType.video, videos, p);
            List<AssetFileDtos.Response> imageResults = uploadMany(packageId, AssetFileType.image, images, p);
            packageService.refreshCountsAndStatus(packageId);
            ContentPackage refreshed = packages.findById(packageId).orElseThrow(() -> BusinessException.notFound("主题包不存在"));
            return new AssetFileDtos.UploadSummary(scriptResults, videoResults, imageResults,
                    refreshed.getScriptCount(), refreshed.getVideoCount(), refreshed.getImageCount(), UploadStatus.success);
        } catch (RuntimeException ex) {
            tasks.markMediaUploadFailed(cp, ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public AssetFile uploadOne(Long packageId, AssetFileType type, MultipartFile file, UserPrincipal p) {
        ContentPackage cp = packages.findById(packageId).orElseThrow(() -> BusinessException.notFound("主题包不存在"));
        packageService.assertCanUploadPackage(cp, p);
        if (Boolean.TRUE.equals(cp.getIsDeleted())) {
            throw BusinessException.badRequest("主题包已删除，不能上传文件");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件不能为空");
        }
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "file" : file.getOriginalFilename();
        StoredObject so = storage.put(packageId, file);
        AssetFile af = new AssetFile();
        af.setFileNo("FILE" + System.currentTimeMillis());
        af.setPackageId(packageId);
        af.setFileType(type);
        af.setType(type.name());
        af.setFileName(originalName);
        af.setOriginalName(originalName);
        af.setMimeType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        af.setFileSize(file.getSize());
        af.setBucketName(so.bucketName());
        af.setObjectKey(so.objectKey());
        af.setPreviewUrl(so.previewUrl());
        af.setThumbnailUrl(so.thumbnailUrl());
        af.setSortOrder(repo.findByPackageId(packageId).size() + 1);
        af.setUploadStatus(UploadStatus.success);
        af.setUploadedBy(p.id());
        af.setCreatedBy(p.id());
        af.setCreatedByName(p.userName());
        af = repo.save(af);
        audit(AuditAction.upload_asset, "asset_file", af.getId(), p.id(), af.getFileName());
        return af;
    }

    public List<AssetFileDtos.Response> list(Long packageId, String fileType, UserPrincipal p) {
        return repo.findByIsDeletedFalse().stream()
                .filter(f -> packageId == null || packageId.equals(f.getPackageId()))
                .filter(f -> canViewFile(f, p))
                .filter(f -> fileType == null || "all".equals(fileType) || f.getFileType().name().equals(fileType))
                .sorted(Comparator.comparing(AssetFile::getCreatedAt).reversed())
                .map(AssetFileDtos::of)
                .toList();
    }

    public AssetFile get(Long id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("文件不存在"));
    }

    public AssetFile getVisible(Long id, UserPrincipal p) {
        AssetFile f = get(id);
        assertCanViewFile(f, p);
        return f;
    }

    @Transactional
    public void softDelete(Long id, UserPrincipal p) {
        AssetFile f = get(id);
        assertCanManageFile(f, p);
        if (Boolean.TRUE.equals(f.getIsDeleted())) {
            return;
        }
        f.setIsDeleted(true);
        f.setDeletedAt(Instant.now());
        f.setDeletedBy(p.id());
        f.setPurgeAt(Instant.now().plusSeconds(7 * 24 * 3600));
        packageService.refreshCountsAndStatus(f.getPackageId());
        audit(AuditAction.delete_asset, "asset_file", id, p.id(), f.getFileName());
    }

    @Transactional
    public AssetFile restore(Long id, UserPrincipal p) {
        AssetFile f = get(id);
        assertCanManageFile(f, p);
        ContentPackage cp = packages.findById(f.getPackageId()).orElseThrow(() -> BusinessException.notFound("主题包不存在"));
        if (Boolean.TRUE.equals(cp.getIsDeleted())) {
            throw BusinessException.badRequest("主题包已删除，不能恢复文件");
        }
        f.setIsDeleted(false);
        f.setDeletedAt(null);
        f.setDeletedBy(null);
        f.setPurgeAt(null);
        f.setUpdatedAt(Instant.now());
        packageService.refreshCountsAndStatus(f.getPackageId());
        audit(AuditAction.restore_asset, "asset_file", id, p.id(), f.getFileName());
        return f;
    }

    @Transactional
    public void purge(Long id, UserPrincipal p) {
        AssetFile f = get(id);
        assertCanManageFile(f, p);
        if (!Boolean.TRUE.equals(f.getIsDeleted())) {
            throw BusinessException.badRequest("文件未在回收站，不能永久删除");
        }
        storage.remove(f.getObjectKey());
        repo.delete(f);
        packageService.refreshCountsAndStatus(f.getPackageId());
        audit(AuditAction.purge_asset, "asset_file", id, p.id(), f.getFileName());
    }

    public List<AssetFileDtos.RecycleBinResponse> recycleBin(String keyword, String fileType, Long deletedBy,
                                                              Long packageId, Long operatorId, UserPrincipal p) {
        return repo.findByIsDeletedTrue().stream()
                .filter(f -> canViewFile(f, p))
                .map(f -> toRecycleResponse(f))
                .filter(r -> keyword == null || r.fileName().contains(keyword) || (r.packageTopicName() != null && r.packageTopicName().contains(keyword)))
                .filter(r -> fileType == null || "all".equals(fileType) || r.fileType().name().equals(fileType))
                .filter(r -> deletedBy == null || deletedBy.equals(r.deletedBy()))
                .filter(r -> packageId == null || packageId.equals(r.packageId()))
                .filter(r -> operatorId == null || operatorId.equals(r.operatorId()))
                .sorted(Comparator.comparing(AssetFileDtos.RecycleBinResponse::deletedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ResponseEntity<InputStreamResource> download(Long id, UserPrincipal p) {
        AssetFile f = getVisible(id, p);
        if (Boolean.TRUE.equals(f.getIsDeleted())) {
            throw BusinessException.notFound("文件不存在");
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(f.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new InputStreamResource(storage.get(f.getObjectKey())));
    }

    public AssetFileDtos.PreviewResponse preview(Long id, UserPrincipal p) {
        AssetFile f = getVisible(id, p);
        if (Boolean.TRUE.equals(f.getIsDeleted())) {
            throw BusinessException.notFound("文件不存在");
        }
        return new AssetFileDtos.PreviewResponse(f.getId(), f.getFileName(), f.getFileType(), f.getMimeType(),
                f.getPreviewUrl(), f.getThumbnailUrl(), f.getFileSize());
    }

    @Transactional
    public void purgeExpired() {
        repo.findByIsDeletedTrueAndPurgeAtLessThanEqual(Instant.now()).forEach(f -> {
            storage.remove(f.getObjectKey());
            repo.delete(f);
        });
    }

    private List<AssetFileDtos.Response> uploadMany(Long packageId, AssetFileType type, List<MultipartFile> files, UserPrincipal p) {
        if (files == null) {
            return List.of();
        }
        List<AssetFileDtos.Response> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(AssetFileDtos.of(uploadOne(packageId, type, file, p)));
        }
        return results;
    }

    private boolean canViewFile(AssetFile f, UserPrincipal p) {
        ContentPackage cp = packages.findById(f.getPackageId()).orElse(null);
        return packageService.canViewPackage(cp, p);
    }

    private void assertCanViewFile(AssetFile f, UserPrincipal p) {
        if (!canViewFile(f, p)) {
            throw BusinessException.forbidden("只能查看与自己绑定的主题包素材");
        }
    }

    private void assertCanManageFile(AssetFile f, UserPrincipal p) {
        ContentPackage cp = packages.findById(f.getPackageId()).orElse(null);
        packageService.assertCanManagePackage(cp, p);
    }

    private AssetFileDtos.RecycleBinResponse toRecycleResponse(AssetFile f) {
        ContentPackage p = packages.findById(f.getPackageId()).orElse(null);
        long remain = f.getPurgeAt() == null ? 0 : Math.max(0, Duration.between(Instant.now(), f.getPurgeAt()).toSeconds());
        return new AssetFileDtos.RecycleBinResponse(f.getId(), f.getFileName(), f.getFileType(), f.getPackageId(),
                p == null ? null : p.getTopicName(), p == null ? null : p.getOperatorId(), p == null ? null : p.getOperatorName(),
                p == null ? null : p.getFullPath(), f.getFileSize(), f.getDeletedBy(), f.getDeletedAt(), f.getPurgeAt(), remain);
    }

    private void audit(AuditAction a, String t, Long tid, Long actor, String d) {
        AuditLog log = new AuditLog();
        log.setAction(a);
        log.setTargetType(t);
        log.setTargetId(tid);
        log.setActorId(actor);
        log.setDetail(d);
        audits.save(log);
    }
}