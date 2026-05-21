package com.treeeducation.ioas.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class UploadTaskWatchdog {
    private static final List<String> WATCH_STATUSES = List.of("created", "uploading", "processing");

    private final TaskRepository taskRepository;
    private final TaskLogService taskLogService;

    @Value("${ioas.task.upload-stale-minutes:15}")
    private long staleMinutes;

    public UploadTaskWatchdog(TaskRepository taskRepository, TaskLogService taskLogService) {
        this.taskRepository = taskRepository;
        this.taskLogService = taskLogService;
    }

    @Scheduled(fixedDelayString = "${ioas.task.upload-watchdog-fixed-delay-ms:60000}")
    public void markStaleUploadTasksFailed() {
        Instant deadline = Instant.now().minus(Math.max(staleMinutes, 1), ChronoUnit.MINUTES);
        List<Task> staleTasks = taskRepository.findByStatusInAndUpdatedAtBefore(WATCH_STATUSES, deadline).stream()
                .filter(task -> task.getRoleType() == TaskRoleType.media)
                .filter(task -> task.getTaskType() == TaskType.media_upload)
                .toList();

        for (Task task : staleTasks) {
            task.setStatus("failed");
            task.setProgress(100);
            task.setErrorMessage("上传任务超过 " + staleMinutes + " 分钟没有进度更新，系统自动判定为中断");
            task.setCompletedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            taskRepository.save(task);
            taskLogService.error(task.getId(), "upload watchdog marked task failed because no progress heartbeat was received for " + staleMinutes + " minutes", null);
        }
    }
}
