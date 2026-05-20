package com.treeeducation.ioas.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByRoleType(TaskRoleType roleType);
    Optional<Task> findFirstByTaskTypeAndRelatedPackageId(TaskType taskType, Long relatedPackageId);
    long countByRoleTypeAndStatus(TaskRoleType roleType, String status);
    long countByRoleTypeAndStatusIn(TaskRoleType roleType, Collection<String> statuses);
    long countByRoleTypeAndAssigneeIdAndStatusIn(TaskRoleType roleType, Long assigneeId, Collection<String> statuses);
    List<Task> findByStatusInAndUpdatedAtBefore(Collection<String> statuses, Instant updatedAtBefore);
    List<Task> findByStatusInAndCompletedAtBefore(Collection<String> statuses, Instant completedAtBefore);
}
