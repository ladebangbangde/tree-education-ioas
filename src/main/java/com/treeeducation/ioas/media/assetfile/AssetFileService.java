package com.treeeducation.ioas.media.assetfile;

import com.treeeducation.ioas.audit.*;
import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.media.contentpackage.ContentPackageService;
import com.treeeducation.ioas.storage.ObjectStorageService;
import com.treeeducation.ioas.storage.StoredObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Application service enforcing package-bound file uploads and recycle-bin semantics. */
@Service
public class AssetFileService {
    private final AssetFileRepository repo;
    private final ContentPackageRepository packages;
    private final ContentPackageService packageService;
    private final ObjectStorageService storage;
    private final AuditLogRepository audits;

    public AssetFileService(AssetFileRepository repo, ContentPackageRepository packages, ContentPackageService packageService,
                            ObjectStorageService storage, AuditLogRepository audits) {
        this.repo = repo;
        this.packages = packages;
        this.packageService = packageService;
        this.storage = storage;
        this.audits = audits;
    }

    @Transactional
    public AssetFileDtos.UploadSummary uploadGrouped(Long packageId, List<MultipartFile> scripts, List<MultipartFile> videos,
                                                     List<MultipartFile> images, UserPrincipal p) {
        List<AssetFileDtos.Response> scriptResults = uploadMany(packageId, AssetFileType.script, scripts, p);
        List<AssetFileDtos.Response> videoResults = uploadMany(packageId, AssetFileType.video, videos, p);
        List<AssetFileDtos.Response> imageResults = uploadMany(packageId, AssetFileType.image, images, p);
        packageService.refreshCountsAndStatus(packageId);
        ContentPackage cp = packages.findById(packageId).orElseThrow(() -> BusinessException.notFound("主题包不存在"));
        return new AssetFileDtos.UploadSummary(scriptResults, videoResults, imageResults,
                cp.getScriptCount(), cp.getVideoCount(), cp.getImageCount(), UploadStatus.success);
    }

    @Transactional
    public AssetFile uploadOne(Long packageId, AssetFileType type, MultipartFile file, UserPrincipal p) {
        packages.findById(packageId).orElseThrow(() -> BusinessException.notFound("主题包不存在"));
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件不能为空");
        }
        StoredObject so = storage.put(packageId, file);
        AssetFile af = new AssetFile();
        af.setFileNo("FILE" + System.currentTimeMillis());
        af.setPackageId(packageId);
        af.setFileType(type);
        af.setFileName(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        af.setMimeType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        af.setFileSize(file.getSize());
        af.setBucketName(so.bucketName());
        af.setObjectKey(so.objectKey());
        af.setPreviewUrl(so.previewUrl());
        af.setThumbnailUrl(so.thumbnailUrl());
        af.setSortOrder(repo.findByPackageId(packageId).size() + 1);
        af.setUploadStatus(UploadStatus.success);
        af.setCreatedBy(p.id());
        af.setCreatedByName(p.userName());
        af = repo.save(af);
        audit(AuditAction.upload_asset, "asset_file", af.getId(), p.id(), af.getFileName());
        return af;
    }

    public List<AssetFileDtos.Response> list(Long packageId, String fileType) {
        return repo.findByIsDeletedFalse().stream()
                .filter(f -> packageId == null || packageId.equals(f.getPackageId()))
                .filter(f -> fileType == null || "all".equals(fileType) || f.getFileType().name().equals(fileType))
                .sorted(Comparator.comparing(AssetFile::getCreatedAt).reversed())
                .map(AssetFileDtos::of)
                .toList();
    }

    public AssetFile get(Long id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("文件不存在"));
    }

    @Transactional
    public void softDelete(Long id, UserPrincipal p) {
        AssetFile f = get(id);
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
        storage.remove(f.getObjectKey());
        repo.delete(f);
        packageService.refreshCountsAndStatus(f.getPackageId());
        audit(AuditAction.purge_asset, "asset_file", id, p.id(), f.getFileName());
    }

    public List<AssetFileDtos.RecycleBinResponse> recycleBin(String keyword, String fileType, Long deletedBy,
                                                              Long packageId, Long operatorId) {
        return repo.findByIsDeletedTrue().stream()
                .map(f -> toRecycleResponse(f))
                .filter(r -> keyword == null || r.fileName().contains(keyword) || (r.packageTopicName() != null && r.packageTopicName().contains(keyword)))
                .filter(r -> fileType == null || "all".equals(fileType) || r.fileType().name().equals(fileType))
                .filter(r -> deletedBy == null || deletedBy.equals(r.deletedBy()))
                .filter(r -> packageId == null || packageId.equals(r.packageId()))
                .filter(r -> operatorId == null || operatorId.equals(r.operatorId()))
                .sorted(Comparator.comparing(AssetFileDtos.RecycleBinResponse::deletedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ResponseEntity<InputStreamResource> download(Long id) {
        AssetFile f = get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getFileName() + "\"")
                .body(new InputStreamResource(storage.get(f.getObjectKey())));
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
