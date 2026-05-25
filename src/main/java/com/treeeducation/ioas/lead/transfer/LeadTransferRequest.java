package com.treeeducation.ioas.lead.transfer;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lead_transfer_request")
public class LeadTransferRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long leadId;
    @Column(length = 40)
    private String leadNo;
    @Column(length = 120)
    private String studentName;
    @Column(nullable = false)
    private Long fromConsultantId;
    @Column(nullable = false, length = 80)
    private String fromConsultantName;
    @Column(nullable = false)
    private Long toConsultantId;
    @Column(nullable = false, length = 80)
    private String toConsultantName;
    @Column(length = 1000)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeadTransferStatus status = LeadTransferStatus.PENDING;
    @Column(nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();
    private Instant respondedAt;
    @Column(length = 1000)
    private String responseRemark;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLeadId() { return leadId; }
    public void setLeadId(Long leadId) { this.leadId = leadId; }
    public String getLeadNo() { return leadNo; }
    public void setLeadNo(String leadNo) { this.leadNo = leadNo; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public Long getFromConsultantId() { return fromConsultantId; }
    public void setFromConsultantId(Long fromConsultantId) { this.fromConsultantId = fromConsultantId; }
    public String getFromConsultantName() { return fromConsultantName; }
    public void setFromConsultantName(String fromConsultantName) { this.fromConsultantName = fromConsultantName; }
    public Long getToConsultantId() { return toConsultantId; }
    public void setToConsultantId(Long toConsultantId) { this.toConsultantId = toConsultantId; }
    public String getToConsultantName() { return toConsultantName; }
    public void setToConsultantName(String toConsultantName) { this.toConsultantName = toConsultantName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LeadTransferStatus getStatus() { return status; }
    public void setStatus(LeadTransferStatus status) { this.status = status; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
    public String getResponseRemark() { return responseRemark; }
    public void setResponseRemark(String responseRemark) { this.responseRemark = responseRemark; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
