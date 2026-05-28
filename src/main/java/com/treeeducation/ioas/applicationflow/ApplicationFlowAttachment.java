package com.treeeducation.ioas.applicationflow;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "application_flow_attachment", indexes = {
        @Index(name = "idx_application_attachment_flow", columnList = "flowId"),
        @Index(name = "idx_application_attachment_step", columnList = "stepId")
})
public class ApplicationFlowAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long flowId;
    @Column(nullable = false) private Long stepId;
    @Column(nullable = false) private Long studentProfileId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40) private ApplicationStepCode stepCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60) private ApplicationAttachmentType attachmentType = ApplicationAttachmentType.OTHER;
    @Column(nullable = false, length = 255) private String originalFilename;
    @Column(nullable = false, length = 120) private String contentType;
    @Column(nullable = false) private Long sizeBytes;
    @Column(nullable = false, length = 1000) private String objectKey;
    @Column(length = 1000) private String fileUrl;
    @Column(length = 600) private String note;
    @Column(nullable = false) private Long uploadedBy;
    @Column(nullable = false, length = 80) private String uploadedByName;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Boolean deleted = false;
    private Instant deletedAt;
    private Long deletedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Long getStepId() { return stepId; }
    public void setStepId(Long stepId) { this.stepId = stepId; }
    public Long getStudentProfileId() { return studentProfileId; }
    public void setStudentProfileId(Long studentProfileId) { this.studentProfileId = studentProfileId; }
    public ApplicationStepCode getStepCode() { return stepCode; }
    public void setStepCode(ApplicationStepCode stepCode) { this.stepCode = stepCode; }
    public ApplicationAttachmentType getAttachmentType() { return attachmentType; }
    public void setAttachmentType(ApplicationAttachmentType attachmentType) { this.attachmentType = attachmentType; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
}
