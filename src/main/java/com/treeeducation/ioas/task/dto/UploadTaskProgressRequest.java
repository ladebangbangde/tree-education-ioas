package com.treeeducation.ioas.task.dto;

public record UploadTaskProgressRequest(
        String status,
        Integer progress,
        Long uploadedBytes,
        Long totalBytes,
        Long speedBytesPerSecond,
        Long averageSpeedBytesPerSecond,
        Integer partCount,
        Integer completedPartCount
) {
}
