package com.treeeducation.ioas.dataops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DataOperationPurgeService {
    private static final String SCOPE_ALL = "ALL";
    private static final String MODE_HARD_DELETE = "HARD_DELETE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;
    private final String defaultBucket;

    public DataOperationPurgeService(JdbcTemplate jdbc,
                                     ObjectMapper objectMapper,
                                     @Value("${ioas.storage.endpoint}") String endpoint,
                                     @Value("${ioas.storage.access-key}") String accessKey,
                                     @Value("${ioas.storage.secret-key}") String secretKey,
                                     @Value("${ioas.storage.bucket:ioas-assets}") String defaultBucket) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.defaultBucket = defaultBucket;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public DataOperationPurgeDtos.PreviewResponse preview(String scopeType) {
        String normalizedScope = normalizeScope(scopeType);
        if (!SCOPE_ALL.equals(normalizedScope)) throw BusinessException.badRequest("当前第一版仅支持清空全部数据操作模块数据");
        int packageCount = count("select count(*) from data_operation_topic_package");
        int topicCount = count("select count(*) from data_operation_platform_topic");
        int contentCount = count("select count(*) from data_operation_content");
        int assetCount = count("select count(*) from data_operation_asset");
        int reportCount = count("select count(*) from data_operation_daily_report");
        int taskCount = count("select count(*) from ioas_task where type in ('DATA_COVER_UPLOAD','DATA_SCREENSHOT_UPLOAD','DATA_DAILY_REPORT')");
        long estimatedFileSize = longValue(jdbc.queryForObject("select coalesce(sum(file_size),0) from data_operation_asset", Object.class));
        return new DataOperationPurgeDtos.PreviewResponse(
                normalizedScope,
                packageCount,
                topicCount,
                contentCount,
                assetCount,
                reportCount,
                taskCount,
                assetCount,
                estimatedFileSize,
                "EXTREME",
                DataOperationPurgeDtos.CONFIRM_TEXT
        );
    }

    @Transactional
    public DataOperationPurgeDtos.JobResponse createHardDeleteJob(DataOperationPurgeDtos.CreateJobRequest request, UserPrincipal principal) {
        if (request == null) throw BusinessException.badRequest("请求不能为空");
        requireSuperAdmin(principal);
        String scope = normalizeScope(request.scopeType());
        String mode = normalizeMode(request.mode());
        if (!SCOPE_ALL.equals(scope)) throw BusinessException.badRequest("当前第一版仅支持清空全部数据操作模块数据");
        if (!MODE_HARD_DELETE.equals(mode)) throw BusinessException.badRequest("当前接口只支持 HARD_DELETE");
        if (!DataOperationPurgeDtos.CONFIRM_TEXT.equals(request.confirmText())) {
            throw BusinessException.badRequest("确认文本不匹配，禁止执行永久删除");
        }
        if (request.reason() == null || request.reason().trim().length() < 2) {
            throw BusinessException.badRequest("请填写删除原因");
        }

        DataOperationPurgeDtos.PreviewResponse preview = preview(scope);
        String purgeNo = "DOP-PURGE-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 8);
        String previewJson = toJson(preview);
        int totalDbRows = preview.packageCount() + preview.topicCount() + preview.contentCount() + preview.assetCount() + preview.reportCount() + preview.taskCount();
        jdbc.update("""
                insert into data_operation_purge_job
                (purge_no, scope_type, mode, status, preview_json, total_db_rows, total_objects, reason, created_by, created_by_name, started_at)
                values (?, ?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?, current_timestamp(6))
                """, purgeNo, scope, mode, previewJson, totalDbRows, preview.minioObjectCount(), request.reason().trim(), currentUserId(principal), currentUserName(principal));
        Long jobId = jdbc.queryForObject("select id from data_operation_purge_job where purge_no = ?", Long.class, purgeNo);

        try {
            snapshotObjects(jobId);
            int deletedDbRows = hardDeleteDatabaseRows();
            MinioDeleteResult minioResult = deleteMinioObjects(jobId);
            String status = minioResult.failedObjects() > 0 ? "PARTIAL_FAILED" : "COMPLETED";
            jdbc.update("""
                    update data_operation_purge_job
                    set status = ?, deleted_db_rows = ?, deleted_objects = ?, failed_objects = ?, finished_at = current_timestamp(6)
                    where id = ?
                    """, status, deletedDbRows, minioResult.deletedObjects(), minioResult.failedObjects(), jobId);
        } catch (RuntimeException ex) {
            jdbc.update("""
                    update data_operation_purge_job
                    set status = 'FAILED', error_message = ?, finished_at = current_timestamp(6)
                    where id = ?
                    """, trim(ex.getMessage(), 4000), jobId);
            throw ex;
        }
        return getJob(jobId);
    }

    public DataOperationPurgeDtos.JobResponse getJob(Long jobId) {
        Map<String, Object> row = queryOne("select * from data_operation_purge_job where id = ?", jobId);
        List<Map<String, Object>> failedSamples = jdbc.queryForList("""
                select id, asset_id, bucket_name, object_key, public_url, error_message, delete_status
                from data_operation_purge_object
                where purge_job_id = ? and delete_status = 'FAILED'
                order by id desc limit 20
                """, jobId);
        return toJobResponse(row, failedSamples);
    }

    @Transactional
    public DataOperationPurgeDtos.JobResponse retryMinio(Long jobId, UserPrincipal principal) {
        requireSuperAdmin(principal);
        queryOne("select * from data_operation_purge_job where id = ?", jobId);
        MinioDeleteResult result = deleteMinioObjects(jobId, true);
        int failedTotal = count("select count(*) from data_operation_purge_object where purge_job_id = " + jobId + " and delete_status = 'FAILED'");
        String status = failedTotal > 0 ? "PARTIAL_FAILED" : "COMPLETED";
        jdbc.update("""
                update data_operation_purge_job
                set status = ?, deleted_objects = deleted_objects + ?, failed_objects = ?, finished_at = current_timestamp(6)
                where id = ?
                """, status, result.deletedObjects(), failedTotal, jobId);
        return getJob(jobId);
    }

    private void snapshotObjects(Long jobId) {
        jdbc.update("""
                insert into data_operation_purge_object
                (purge_job_id, asset_id, bucket_name, object_key, public_url, file_size, delete_status)
                select ?, id, coalesce(nullif(bucket_name,''), ?), object_key, public_url, coalesce(file_size,0), 'PENDING'
                from data_operation_asset
                where object_key is not null and object_key <> ''
                """, jobId, defaultBucket);
    }

    private int hardDeleteDatabaseRows() {
        int total = 0;
        total += jdbc.update("delete from ioas_task where type in ('DATA_COVER_UPLOAD','DATA_SCREENSHOT_UPLOAD','DATA_DAILY_REPORT')");
        total += jdbc.update("delete from data_operation_asset");
        total += jdbc.update("delete from data_operation_content");
        total += jdbc.update("delete from data_operation_platform_topic");
        total += jdbc.update("delete from data_operation_daily_report");
        total += jdbc.update("delete from data_operation_topic_package");
        return total;
    }

    private MinioDeleteResult deleteMinioObjects(Long jobId) {
        return deleteMinioObjects(jobId, false);
    }

    private MinioDeleteResult deleteMinioObjects(Long jobId, boolean failedOnly) {
        String sql = failedOnly
                ? "select * from data_operation_purge_object where purge_job_id = ? and delete_status = 'FAILED' order by id asc"
                : "select * from data_operation_purge_object where purge_job_id = ? and delete_status = 'PENDING' order by id asc";
        List<Map<String, Object>> objects = jdbc.queryForList(sql, jobId);
        int deleted = 0;
        int failed = 0;
        for (Map<String, Object> object : objects) {
            Long id = numberToLong(object.get("id"));
            String bucket = stringValue(object.get("bucket_name"));
            String objectKey = stringValue(object.get("object_key"));
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket == null || bucket.isBlank() ? defaultBucket : bucket)
                        .object(objectKey)
                        .build());
                jdbc.update("update data_operation_purge_object set delete_status = 'SUCCESS', error_message = null, deleted_at = current_timestamp(6) where id = ?", id);
                deleted++;
            } catch (Exception ex) {
                jdbc.update("update data_operation_purge_object set delete_status = 'FAILED', error_message = ? where id = ?", trim(ex.getMessage(), 4000), id);
                failed++;
            }
        }
        return new MinioDeleteResult(deleted, failed);
    }

    private DataOperationPurgeDtos.JobResponse toJobResponse(Map<String, Object> row, List<Map<String, Object>> failedSamples) {
        Object preview = parseJson(stringValue(row.get("preview_json")));
        return new DataOperationPurgeDtos.JobResponse(
                numberToLong(row.get("id")),
                stringValue(row.get("purge_no")),
                stringValue(row.get("scope_type")),
                stringValue(row.get("mode")),
                stringValue(row.get("status")),
                intValue(row.get("total_db_rows")),
                intValue(row.get("total_objects")),
                intValue(row.get("deleted_db_rows")),
                intValue(row.get("deleted_objects")),
                intValue(row.get("failed_objects")),
                stringValue(row.get("reason")),
                stringValue(row.get("error_message")),
                preview,
                failedSamples
        );
    }

    private void requireSuperAdmin(UserPrincipal principal) {
        if (principal == null || !"SUPER_ADMIN".equalsIgnoreCase(principal.role())) {
            throw BusinessException.forbidden("只有超级管理员可以执行数据操作模块永久删除");
        }
    }

    private String normalizeScope(String scopeType) {
        String scope = scopeType == null || scopeType.isBlank() ? SCOPE_ALL : scopeType.trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_ALL.equals(scope)) throw BusinessException.badRequest("当前第一版仅支持 ALL");
        return scope;
    }

    private String normalizeMode(String mode) {
        String value = mode == null || mode.isBlank() ? MODE_HARD_DELETE : mode.trim().toUpperCase(Locale.ROOT);
        if (!MODE_HARD_DELETE.equals(value)) throw BusinessException.badRequest("当前第一版仅支持 HARD_DELETE");
        return value;
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw BusinessException.notFound("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private Object parseJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception ex) {
            return value;
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Long currentUserId(UserPrincipal principal) {
        return principal == null ? 0L : principal.id();
    }

    private String currentUserName(UserPrincipal principal) {
        return principal == null ? "system" : principal.userName();
    }

    private record MinioDeleteResult(int deletedObjects, int failedObjects) {}
}
