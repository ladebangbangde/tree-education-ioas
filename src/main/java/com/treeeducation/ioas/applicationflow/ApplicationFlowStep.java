package com.treeeducation.ioas.applicationflow;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "application_flow_step", uniqueConstraints = {
        @UniqueConstraint(name = "uk_application_flow_step", columnNames = {"flowId", "stepCode"})
})
public class ApplicationFlowStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long flowId;
    @Column(nullable = false) private Long studentProfileId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40) private ApplicationStepCode stepCode;
    @Column(nullable = false) private Integer orderNo;
    @Column(nullable = false, length = 120) private String stepName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40) private ApplicationStepStatus status = ApplicationStepStatus.LOCKED;
    @Column(nullable = false) private Boolean required = true;
    @Column(nullable = false) private Integer uploadedFileCount = 0;
    @Column(length = 1000) private String consultantNote;
    @Column(length = 1000) private String customerVisibleNote;
    private Instant startedAt;
    private Instant completedAt;
    private Long completedBy;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @Version private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Long getStudentProfileId() { return studentProfileId; }
    public void setStudentProfileId(Long studentProfileId) { this.studentProfileId = studentProfileId; }
    public ApplicationStepCode getStepCode() { return stepCode; }
    public void setStepCode(ApplicationStepCode stepCode) { this.stepCode = stepCode; }
    public Integer getOrderNo() { return orderNo; }
    public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public ApplicationStepStatus getStatus() { return status; }
    public void setStatus(ApplicationStepStatus status) { this.status = status; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public Integer getUploadedFileCount() { return uploadedFileCount; }
    public void setUploadedFileCount(Integer uploadedFileCount) { this.uploadedFileCount = uploadedFileCount; }
    public String getConsultantNote() { return consultantNote; }
    public void setConsultantNote(String consultantNote) { this.consultantNote = consultantNote; }
    public String getCustomerVisibleNote() { return customerVisibleNote; }
    public void setCustomerVisibleNote(String customerVisibleNote) { this.customerVisibleNote = customerVisibleNote; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Long getCompletedBy() { return completedBy; }
    public void setCompletedBy(Long completedBy) { this.completedBy = completedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
