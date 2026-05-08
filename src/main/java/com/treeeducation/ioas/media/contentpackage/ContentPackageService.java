package com.treeeducation.ioas.media.contentpackage;

import com.treeeducation.ioas.audit.*;
import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.media.assetfile.*;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfileRepository;
import com.treeeducation.ioas.task.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/** Application service for media topic packages. */
@Service
public class ContentPackageService {
    private final ContentPackageRepository repo;
    private final AssetFileRepository files;
    private final OperatorProfileRepository operators;
    private final AuditLogRepository audits;
    private final TaskService tasks;

    public ContentPackageService(ContentPackageRepository repo, AssetFileRepository files, OperatorProfileRepository operators,
                                 AuditLogRepository audits, TaskService tasks) {
        this.repo = repo;
        this.files = files;
        this.operators = operators;
        this.audits = audits;
        this.tasks = tasks;
    }

    @Transactional
    public ContentPackage create(ContentPackageDtos.UpsertRequest r, UserPrincipal p) {
        var operator = operators.findById(r.operatorId()).orElseThrow(() -> BusinessException.notFound("运营人员不存在"));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        ContentPackage cp = new ContentPackage();
        cp.setPackageNo("PKG" + System.currentTimeMillis());
        cp.setTopicName(r.topicName());
        cp.setOperatorId(operator.getId());
        cp.setOperatorName(operator.getName());
        cp.setFolderYear(today.getYear());
        cp.setFolderMonth(today.getMonthValue());
        cp.setFolderDay(today.getDayOfMonth());
        cp.setFullPath(buildPath(today, operator.getName(), r.topicName()));
        cp.setUploadStatus(ContentPackageStatus.pending_upload);
        cp.setCreatedBy(p.id());
        cp.setCreatedByName(p.userName());
        cp = repo.save(cp);
        tasks.ensureMediaUploadTask(cp);
        audit(AuditAction.create_package, "content_package", cp.getId(), p.id(), cp.getTopicName());
        return cp;
    }

    public List<ContentPackageDtos.Response> list(String keyword, Long operatorId, ContentPackageStatus status, String tab, UserPrincipal principal) {
        return repo.findAll().stream()
                .filter(p -> "recycle".equals(tab) ? p.getIsDeleted() : !p.getIsDeleted())
                .filter(p -> keyword == null || p.getTopicName().contains(keyword) || (p.getFullPath() != null && p.getFullPath().contains(keyword)))
                .filter(p -> operatorId == null || operatorId.equals(p.getOperatorId()))
                .filter(p -> status == null || status == p.getUploadStatus())
                .filter(p -> !"mine".equals(tab) || p.getCreatedBy().equals(principal.id()))
                .filter(p -> !"draft".equals(tab) || p.getUploadStatus() == ContentPackageStatus.pending_upload)
                .filter(p -> !"record".equals(tab) || p.getUploadStatus() != ContentPackageStatus.pending_upload)
                .sorted(Comparator.comparing(ContentPackage::getCreatedAt).reversed())
                .map(ContentPackageDtos::of)
                .toList();
    }

    public ContentPackage get(Long id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("主题包不存在"));
    }

    public ContentPackageDtos.DetailResponse detail(Long id) {
        ContentPackage p = get(id);
        if (Boolean.TRUE.equals(p.getIsDeleted())) {
            throw BusinessException.notFound("主题包不存在");
        }
        List<AssetFileDtos.Response> all = files.findByPackageIdAndIsDeletedFalse(id).stream().map(AssetFileDtos::of).toList();
        return new ContentPackageDtos.DetailResponse(ContentPackageDtos.of(p),
                all.stream().filter(f -> f.fileType() == AssetFileType.script).toList(),
                all.stream().filter(f -> f.fileType() == AssetFileType.video).toList(),
                all.stream().filter(f -> f.fileType() == AssetFileType.image).toList());
    }

    @Transactional
    public ContentPackage update(Long id, ContentPackageDtos.UpsertRequest r, UserPrincipal p) {
        ContentPackage cp = get(id);
        var operator = operators.findById(r.operatorId()).orElseThrow(() -> BusinessException.notFound("运营人员不存在"));
        cp.setOperatorId(operator.getId());
        cp.setOperatorName(operator.getName());
        cp.setTopicName(r.topicName());
        cp.setFullPath(buildPath(LocalDate.of(cp.getFolderYear(), cp.getFolderMonth(), cp.getFolderDay()), operator.getName(), r.topicName()));
        cp.setUpdatedAt(Instant.now());
        audit(AuditAction.update_package, "content_package", id, p.id(), cp.getTopicName());
        return cp;
    }

    @Transactional
    public void delete(Long id, UserPrincipal p) {
        ContentPackage cp = get(id);
        cp.setIsDeleted(true);
        cp.setUploadStatus(ContentPackageStatus.deleted);
        cp.setDeletedAt(Instant.now());
        cp.setDeletedBy(p.id());
        files.findByPackageIdAndIsDeletedFalse(id).forEach(f -> {
            f.setIsDeleted(true);
            f.setDeletedAt(Instant.now());
            f.setDeletedBy(p.id());
            f.setPurgeAt(Instant.now().plusSeconds(7 * 24 * 3600));
        });
        audit(AuditAction.delete_package, "content_package", id, p.id(), cp.getTopicName());
    }

    @Transactional
    public void refreshCountsAndStatus(Long id) {
        ContentPackage cp = get(id);
        var list = files.findByPackageIdAndIsDeletedFalse(id);
        cp.setScriptCount((int) list.stream().filter(f -> f.getFileType() == AssetFileType.script).count());
        cp.setVideoCount((int) list.stream().filter(f -> f.getFileType() == AssetFileType.video).count());
        cp.setImageCount((int) list.stream().filter(f -> f.getFileType() == AssetFileType.image).count());
        boolean hasAll = cp.getScriptCount() > 0 && cp.getVideoCount() > 0 && cp.getImageCount() > 0;
        boolean hasAny = cp.getScriptCount() > 0 || cp.getVideoCount() > 0 || cp.getImageCount() > 0;
        cp.setUploadStatus(hasAll ? ContentPackageStatus.completed : hasAny ? ContentPackageStatus.partial_completed : ContentPackageStatus.pending_upload);
        cp.setUpdatedAt(Instant.now());
        tasks.refreshMediaUploadTask(cp);
    }

    private String buildPath(LocalDate date, String operatorName, String topicName) {
        return "/" + date.getYear() + "/" + String.format("%02d", date.getMonthValue()) + "/" + String.format("%02d", date.getDayOfMonth()) + "/" + operatorName + "/" + topicName;
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
