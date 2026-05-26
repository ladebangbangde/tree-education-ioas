package com.treeeducation.ioas.system.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "consultant_region_assignment")
public class ConsultantRegionAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long consultantProfileId;
    @Column(nullable = false)
    private Long consultantUserId;
    @Column(nullable = false)
    private Long regionId;
    @Column(nullable = false, length = 40)
    private String regionCode;
    @Column(nullable = false, length = 80)
    private String regionName;
    @Column(nullable = false)
    private Integer priority = 100;
    @Column(nullable = false)
    private Integer otherAssignCount = 0;
    @Column(nullable = false)
    private Boolean enabled = true;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConsultantProfileId() { return consultantProfileId; }
    public void setConsultantProfileId(Long consultantProfileId) { this.consultantProfileId = consultantProfileId; }
    public Long getConsultantUserId() { return consultantUserId; }
    public void setConsultantUserId(Long consultantUserId) { this.consultantUserId = consultantUserId; }
    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getOtherAssignCount() { return otherAssignCount == null ? 0 : otherAssignCount; }
    public void setOtherAssignCount(Integer otherAssignCount) { this.otherAssignCount = otherAssignCount == null ? 0 : otherAssignCount; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}