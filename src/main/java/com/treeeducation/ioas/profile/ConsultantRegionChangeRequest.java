package com.treeeducation.ioas.profile;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "consultant_region_change_request")
public class ConsultantRegionChangeRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long consultantUserId;
    @Column(nullable = false) private Long consultantProfileId;
    @Column(nullable = false, length = 80) private String consultantName;
    @Column(length = 255) private String currentRegionCodes;
    @Column(length = 255) private String currentRegionNames;
    @Column(nullable = false, length = 255) private String requestedRegionCodes;
    @Column(nullable = false, length = 255) private String requestedRegionNames;
    @Column(length = 1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ConsultantRegionChangeStatus status = ConsultantRegionChangeStatus.PENDING;
    private Long reviewerUserId;
    @Column(length = 80) private String reviewerName;
    @Column(length = 1000) private String reviewRemark;
    @Column(nullable = false, updatable = false) private Instant requestedAt = Instant.now();
    private Instant reviewedAt;
    private Instant updatedAt = Instant.now();

    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getConsultantUserId(){return consultantUserId;} public void setConsultantUserId(Long consultantUserId){this.consultantUserId=consultantUserId;}
    public Long getConsultantProfileId(){return consultantProfileId;} public void setConsultantProfileId(Long consultantProfileId){this.consultantProfileId=consultantProfileId;}
    public String getConsultantName(){return consultantName;} public void setConsultantName(String consultantName){this.consultantName=consultantName;}
    public String getCurrentRegionCodes(){return currentRegionCodes;} public void setCurrentRegionCodes(String currentRegionCodes){this.currentRegionCodes=currentRegionCodes;}
    public String getCurrentRegionNames(){return currentRegionNames;} public void setCurrentRegionNames(String currentRegionNames){this.currentRegionNames=currentRegionNames;}
    public String getRequestedRegionCodes(){return requestedRegionCodes;} public void setRequestedRegionCodes(String requestedRegionCodes){this.requestedRegionCodes=requestedRegionCodes;}
    public String getRequestedRegionNames(){return requestedRegionNames;} public void setRequestedRegionNames(String requestedRegionNames){this.requestedRegionNames=requestedRegionNames;}
    public String getReason(){return reason;} public void setReason(String reason){this.reason=reason;}
    public ConsultantRegionChangeStatus getStatus(){return status;} public void setStatus(ConsultantRegionChangeStatus status){this.status=status;}
    public Long getReviewerUserId(){return reviewerUserId;} public void setReviewerUserId(Long reviewerUserId){this.reviewerUserId=reviewerUserId;}
    public String getReviewerName(){return reviewerName;} public void setReviewerName(String reviewerName){this.reviewerName=reviewerName;}
    public String getReviewRemark(){return reviewRemark;} public void setReviewRemark(String reviewRemark){this.reviewRemark=reviewRemark;}
    public Instant getRequestedAt(){return requestedAt;} public void setRequestedAt(Instant requestedAt){this.requestedAt=requestedAt;}
    public Instant getReviewedAt(){return reviewedAt;} public void setReviewedAt(Instant reviewedAt){this.reviewedAt=reviewedAt;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant updatedAt){this.updatedAt=updatedAt;}
}
