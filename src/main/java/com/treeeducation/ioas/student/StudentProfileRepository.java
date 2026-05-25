package com.treeeducation.ioas.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findBySourceLeadId(Long sourceLeadId);
    List<StudentProfile> findByOwnerConsultantIdAndProfileStatusNotOrderByCreatedAtDesc(Long ownerConsultantId, StudentProfileStatus profileStatus);
    List<StudentProfile> findByProfileStatusNotOrderByCreatedAtDesc(StudentProfileStatus profileStatus);
}
