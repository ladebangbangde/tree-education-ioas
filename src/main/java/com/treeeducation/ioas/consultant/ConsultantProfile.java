package com.treeeducation.ioas.consultant;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "consultant_profile")
public class ConsultantProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private Long userId;
    @Column(nullable = false, length = 80) private String consultantName;
    @Column(length = 40) private String phone;
    @Column(length = 120) private String email;
    @Column(length = 80) private String teamName;
    @Column(nullable = false) private Boolean enabled = true;
    @Column(nullable = false) private Boolean assignEnabled = true;
    @Column(nullable = false) private Integer maxDailyLeads = 30;
    @Column(nullable = false) private Integer currentDailyLeads = 0;
    private Instant lastAssignedAt;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConsultantName() { return consultantName; }
    public void setConsultantName(String consultantName) { this.consultantName = consultantName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getAssignEnabled() { return assignEnabled; }
    public void setAssignEnabled(Boolean assignEnabled) { this.assignEnabled = assignEnabled; }
    public Integer getMaxDailyLeads() { return maxDailyLeads; }
    public void setMaxDailyLeads(Integer maxDailyLeads) { this.maxDailyLeads = maxDailyLeads; }
    public Integer getCurrentDailyLeads() { return currentDailyLeads; }
    public void setCurrentDailyLeads(Integer currentDailyLeads) { this.currentDailyLeads = currentDailyLeads; }
    public Instant getLastAssignedAt() { return lastAssignedAt; }
    public void setLastAssignedAt(Instant lastAssignedAt) { this.lastAssignedAt = lastAssignedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
