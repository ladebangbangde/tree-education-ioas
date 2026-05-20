package com.treeeducation.ioas.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class TaskCleanupScheduler {
    private static final List<String> RUNNING_STATUSES = List.of("created", "queued", "uploading", "processing");
    private static final List<String> SHORT_RETENTION_STATUSES = List.of("failed", "cancelled");
    private static final List<String> LONG_RETENTION_STATUSES = List.of("success");

    private final TaskRepository taskRepository;
    private final TaskLogService taskLogService;

    public TaskCleanupScheduler(TaskRepository taskRepository, TaskLogService taskLogService) {
        this.taskRepository = taskRepository;
        this.taskLogService = taskLogService;
    }

    @Scheduled(fixedDelayString = "${ioas.task.cleanup.fixed-delay-ms:300000}")
    @Transactional
    public void cleanup() {
        failStaleRunningTasks();
        deleteExpiredTerminalTasks(SHORT_RETENTION_STATUSES, 7);
        deleteExpiredTerminalTasks(LONG_RETENTION_STATUSES, 30);
    }

    private void failStaleRunningTasks() {
        Instant threshold = Instant.now().minus(30, ChronoUnit.MINUTES);
        List<Task> staleTasks = taskRepository.findByStatusInAndUpdatedAtBefore(RUNNING_STATUSES, threshold);
        for (Task task : staleTasks) {
            task.setStatus("failed");
            task.setProgress(100);
            task.setErrorMessage("任务超过30分钟无进度更新，系统自动标记失败");
            task.setCompletedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            taskRepository.save(task);
            taskLogService.warn(task.getId(), "task stale timeout, auto marked failed");
        }
    }

    private void deleteExpiredTerminalTasks(List<String> statuses, int retentionDays) {
        Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<Task> expiredTasks = taskRepository.findByStatusInAndCompletedAtBefore(statuses, threshold);
        for (Task task : expiredTasks) {
            Long taskId = task.getId();
            taskRepository.delete(task);
            taskLogService.delete(taskId);
        }
    }
}
