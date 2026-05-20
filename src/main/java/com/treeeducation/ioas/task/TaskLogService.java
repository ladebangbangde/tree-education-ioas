package com.treeeducation.ioas.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

@Service
public class TaskLogService {
    @Value("${ioas.task.log-dir:/app/logs/upload-tasks}")
    private String logDir;

    public void info(Long taskId, String message) {
        append(taskId, "INFO", message, null);
    }

    public void warn(Long taskId, String message) {
        append(taskId, "WARN", message, null);
    }

    public void error(Long taskId, String message, Throwable error) {
        append(taskId, "ERROR", message, error);
    }

    public List<String> tail(Long taskId, int maxLines) {
        try {
            Path file = filePath(taskId);
            if (!Files.exists(file)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - Math.max(1, maxLines));
            return lines.subList(from, lines.size());
        } catch (IOException ex) {
            return List.of("ERROR " + Instant.now() + " read task log failed: " + ex.getMessage());
        }
    }

    public void delete(Long taskId) {
        try {
            Files.deleteIfExists(filePath(taskId));
        } catch (IOException ignored) {
            // Log cleanup must never break scheduler flow.
        }
    }

    private synchronized void append(Long taskId, String level, String message, Throwable error) {
        try {
            Path file = filePath(taskId);
            Files.createDirectories(file.getParent());
            StringBuilder line = new StringBuilder();
            line.append(Instant.now()).append(' ')
                    .append(level).append(' ')
                    .append("taskId=").append(taskId).append(' ')
                    .append(message == null ? "" : message);
            if (error != null) {
                line.append(" | ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage());
            }
            line.append(System.lineSeparator());
            Files.writeString(file, line.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Task logging must never break upload business flow.
        }
    }

    private Path filePath(Long taskId) {
        long safeTaskId = taskId == null ? 0L : taskId;
        return Path.of(logDir).resolve("task-" + safeTaskId + ".log");
    }
}
