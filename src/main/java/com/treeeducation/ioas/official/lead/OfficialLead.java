package com.treeeducation.ioas.official.lead;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "official_lead")
public class OfficialLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "age", nullable = false, length = 20)
    private String age;

    @Column(name = "education", nullable = false, length = 120)
    private String education;

    @Column(name = "city", nullable = false, length = 120)
    private String city;

    @Column(name = "phone", nullable = false, length = 40)
    private String phone;

    @Column(name = "wechat", length = 80)
    private String wechat;

    @Column(name = "destination", nullable = false, length = 120)
    private String destination;

    @Column(name = "budget", nullable = false, length = 80)
    private String budget;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "source", nullable = false, length = 120)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OfficialLeadStatus status = OfficialLeadStatus.NEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWechat() { return wechat; }
    public void setWechat(String wechat) { this.wechat = wechat; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public OfficialLeadStatus getStatus() { return status; }
    public void setStatus(OfficialLeadStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
