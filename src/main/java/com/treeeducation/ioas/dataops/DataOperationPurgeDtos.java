package com.treeeducation.ioas.dataops;

import java.util.List;
import java.util.Map;

public final class DataOperationPurgeDtos {
    private DataOperationPurgeDtos() {}

    public static final String CONFIRM_TEXT = "我确认永久删除数据操作模块全部数据和MinIO文件";

    public record PreviewResponse(
            String scopeType,
            int packageCount,
            int topicCount,
            int contentCount,
            int assetCount,
            int reportCount,
            int taskCount,
            int minioObjectCount,
            long estimatedFileSize,
            String riskLevel,
            String confirmText
    ) {}

    public record CreateJobRequest(
            String scopeType,
            String mode,
            String confirmText,
            String reason
    ) {}

    public record JobResponse(
            Long id,
            String purgeNo,
            String scopeType,
            String mode,
            String status,
            int totalDbRows,
            int totalObjects,
            int deletedDbRows,
            int deletedObjects,
            int failedObjects,
            String reason,
            String errorMessage,
            Object preview,
            List<Map<String, Object>> failedObjectSamples
    ) {}
}
