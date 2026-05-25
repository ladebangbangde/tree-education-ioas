package com.treeeducation.ioas.student;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student_profile")
public class StudentProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String studentNo;
    @Column(nullable = false)
    private Long sourceLeadId;
    @Column(length = 40)
    private String sourceLeadNo;
    @Column(nullable = false)
    private Long ownerConsultantId;
    @Column(nullable = false, length = 80)
    private String ownerConsultantName;
    @Column(nullable = false, length = 120)
    private String studentName;
    @Column(length = 40)
    private String phone;
    @Column(length = 80)
    private String wechat;
    @Column(length = 20)
    private String age;
    @Column(length = 80)
    private String educationLevel;
    @Column(length = 80)
    private String province;
    @Column(length = 80)
    private String city;
    @Column(length = 160)
    private String locationText;
    private Long intentionRegionId;
    @Column(length = 40)
    private String intentionRegionCode;
    @Column(length = 80)
    private String intentionRegionName;
    @Column(length = 80)
    private String targetCountry;
    @Column(length = 120)
    private String targetMajor;
    @Column(length = 80)
    private String budget;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StudentProfileStatus profileStatus = StudentProfileStatus.ACTIVE;
    @Column(length = 1000)
    private String remark;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public Long getSourceLeadId() { return sourceLeadId; }
    public void setSourceLeadId(Long sourceLeadId) { this.sourceLeadId = sourceLeadId; }
    public String getSourceLeadNo() { return sourceLeadNo; }
    public void setSourceLeadNo(String sourceLeadNo) { this.sourceLeadNo = sourceLeadNo; }
    public Long getOwnerConsultantId() { return ownerConsultantId; }
    public void setOwnerConsultantId(Long ownerConsultantId) { this.ownerConsultantId = ownerConsultantId; }
    public String getOwnerConsultantName() { return ownerConsultantName; }
    public void setOwnerConsultantName(String ownerConsultantName) { this.ownerConsultantName = ownerConsultantName; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWechat() { return wechat; }
    public void setWechat(String wechat) { this.wechat = wechat; }
    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }
    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getLocationText() { return locationText; }
    public void setLocationText(String locationText) { this.locationText = locationText; }
    public Long getIntentionRegionId() { return intentionRegionId; }
    public void setIntentionRegionId(Long intentionRegionId) { this.intentionRegionId = intentionRegionId; }
    public String getIntentionRegionCode() { return intentionRegionCode; }
    public void setIntentionRegionCode(String intentionRegionCode) { this.intentionRegionCode = intentionRegionCode; }
    public String getIntentionRegionName() { return intentionRegionName; }
    public void setIntentionRegionName(String intentionRegionName) { this.intentionRegionName = intentionRegionName; }
    public String getTargetCountry() { return targetCountry; }
    public void setTargetCountry(String targetCountry) { this.targetCountry = targetCountry; }
    public String getTargetMajor() { return targetMajor; }
    public void setTargetMajor(String targetMajor) { this.targetMajor = targetMajor; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public StudentProfileStatus getProfileStatus() { return profileStatus; }
    public void setProfileStatus(StudentProfileStatus profileStatus) { this.profileStatus = profileStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
