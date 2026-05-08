package com.treeeducation.ioas.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByRoleType(TaskRoleType roleType);
    Optional<Task> findFirstByTaskTypeAndRelatedPackageId(TaskType taskType, Long relatedPackageId);
    long countByRoleTypeAndStatus(TaskRoleType roleType, String status);
}
