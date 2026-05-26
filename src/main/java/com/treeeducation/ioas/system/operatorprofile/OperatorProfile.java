package com.treeeducation.ioas.system.operatorprofile;

import jakarta.persistence.*;
import java.time.Instant;

/** Operator/consultant profile used by lead/task assignment and dropdowns. */
@Entity
@Table(name = "operator_profile")
public class OperatorProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 80) private String name;
    @Column(length = 40) private String phone;
    @Column(length = 80) private String teamName;
    @Column(nullable = false) private Boolean enabled = true;
    @Column(length = 100) private String consultantQrBucketName;
    @Column(length = 500) private String consultantQrObjectKey;
    @Column(length = 1000) private String consultantQrPublicUrl;
    @Column(length = 1000) private String consultantAvatarPublicUrl;
    @Column(length = 120) private String publicTitle;
    @Column(length = 500) private String publicBio;
    @Column(length = 255) private String specialityRegionCodes;
    @Column(length = 255) private String specialityRegionNames;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public String getPhone(){return phone;} public void setPhone(String phone){this.phone=phone;}
    public String getTeamName(){return teamName;} public void setTeamName(String teamName){this.teamName=teamName;}
    public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean enabled){this.enabled=enabled;}
    public String getConsultantQrBucketName(){return consultantQrBucketName;} public void setConsultantQrBucketName(String consultantQrBucketName){this.consultantQrBucketName=consultantQrBucketName;}
    public String getConsultantQrObjectKey(){return consultantQrObjectKey;} public void setConsultantQrObjectKey(String consultantQrObjectKey){this.consultantQrObjectKey=consultantQrObjectKey;}
    public String getConsultantQrPublicUrl(){return consultantQrPublicUrl;} public void setConsultantQrPublicUrl(String consultantQrPublicUrl){this.consultantQrPublicUrl=consultantQrPublicUrl;}
    public String getConsultantAvatarPublicUrl(){return consultantAvatarPublicUrl;} public void setConsultantAvatarPublicUrl(String consultantAvatarPublicUrl){this.consultantAvatarPublicUrl=consultantAvatarPublicUrl;}
    public String getPublicTitle(){return publicTitle;} public void setPublicTitle(String publicTitle){this.publicTitle=publicTitle;}
    public String getPublicBio(){return publicBio;} public void setPublicBio(String publicBio){this.publicBio=bioOrNull(publicBio);}
    public String getSpecialityRegionCodes(){return specialityRegionCodes;} public void setSpecialityRegionCodes(String specialityRegionCodes){this.specialityRegionCodes=specialityRegionCodes;}
    public String getSpecialityRegionNames(){return specialityRegionNames;} public void setSpecialityRegionNames(String specialityRegionNames){this.specialityRegionNames=specialityRegionNames;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;}

    private String bioOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}