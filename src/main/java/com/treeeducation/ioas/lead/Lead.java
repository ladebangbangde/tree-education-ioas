package com.treeeducation.ioas.lead;

import jakarta.persistence.*;

import java.time.Instant;

/** Operation lead generated from a content package. */
@Entity
@Table(name = "lead_record")
public class Lead {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 40) private String leadNo;
    @Column(length = 40) private String sourceType;
    private Long relatedPackageId;
    private Long operatorId;
    @Column(nullable = false, length = 120) private String studentName;
    @Column(length = 40) private String phone;
    @Column(length = 80) private String wechat;
    @Column(length = 80) private String sourceChannel;
    @Column(length = 80) private String targetCountry;
    @Column(length = 120) private String targetMajor;
    @Column(length = 80) private String budget;
    @Column(length = 80) private String degreeLevel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private LeadStatus status = LeadStatus.unassigned;
    private Long assignedTo;
    @Column(length = 80) private String assignedToName;
    @Column(length = 1000) private String remark;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLeadNo() { return leadNo; }
    public void setLeadNo(String leadNo) { this.leadNo = leadNo; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getRelatedPackageId() { return relatedPackageId; }
    public void setRelatedPackageId(Long relatedPackageId) { this.relatedPackageId = relatedPackageId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWechat() { return wechat; }
    public void setWechat(String wechat) { this.wechat = wechat; }
    public String getSourceChannel() { return sourceChannel; }
    public void setSourceChannel(String sourceChannel) { this.sourceChannel = sourceChannel; }
    public String getTargetCountry() { return targetCountry; }
    public void setTargetCountry(String targetCountry) { this.targetCountry = targetCountry; }
    public String getTargetMajor() { return targetMajor; }
    public void setTargetMajor(String targetMajor) { this.targetMajor = targetMajor; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public String getDegreeLevel() { return degreeLevel; }
    public void setDegreeLevel(String degreeLevel) { this.degreeLevel = degreeLevel; }
    public LeadStatus getStatus() { return status; }
    public void setStatus(LeadStatus status) { this.status = status; }
    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long assignedTo) { this.assignedTo = assignedTo; }
    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
