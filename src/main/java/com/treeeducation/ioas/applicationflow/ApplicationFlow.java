package com.treeeducation.ioas.applicationflow;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "application_flow")
public class ApplicationFlow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long studentProfileId;
    @Column(length = 40)
    private String studentNo;
    @Column(nullable = false, length = 120)
    private String studentName;
    @Column(nullable = false)
    private Long ownerConsultantId;
    @Column(nullable = false, length = 80)
    private String ownerConsultantName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApplicationStepCode currentStep = ApplicationStepCode.PREPARE_MATERIALS;
    @Column(nullable = false)
    private Integer progressPercent = 0;
    @Column(nullable = false)
    private Boolean completed = false;
    @Column(length = 1000)
    private String remark;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentProfileId() { return studentProfileId; }
    public void setStudentProfileId(Long studentProfileId) { this.studentProfileId = studentProfileId; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public Long getOwnerConsultantId() { return ownerConsultantId; }
    public void setOwnerConsultantId(Long ownerConsultantId) { this.ownerConsultantId = ownerConsultantId; }
    public String getOwnerConsultantName() { return ownerConsultantName; }
    public void setOwnerConsultantName(String ownerConsultantName) { this.ownerConsultantName = ownerConsultantName; }
    public ApplicationStepCode getCurrentStep() { return currentStep; }
    public void setCurrentStep(ApplicationStepCode currentStep) { this.currentStep = currentStep; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
