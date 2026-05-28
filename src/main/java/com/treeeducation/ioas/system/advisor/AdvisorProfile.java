package com.treeeducation.ioas.system.advisor;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "advisor_profile")
public class AdvisorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;
    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;
    @Column(name = "gender", nullable = false, length = 20)
    private String gender;
    @Column(name = "responsible_region", nullable = false, length = 40)
    private String responsibleRegion;
    @Column(name = "location_region", nullable = false, length = 40)
    private String locationRegion;
    @Column(name = "bio", nullable = false, length = 1000)
    private String bio;
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getResponsibleRegion() { return responsibleRegion; }
    public void setResponsibleRegion(String responsibleRegion) { this.responsibleRegion = responsibleRegion; }
    public String getLocationRegion() { return locationRegion; }
    public void setLocationRegion(String locationRegion) { this.locationRegion = locationRegion; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
