package com.treeeducation.ioas.media.contentpackage;

import jakarta.persistence.*;
import java.time.Instant;

/** Media topic package; all asset files must belong to one package. */
@Entity @Table(name = "content_package")
public class ContentPackage {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=120) private String name; @Column(length=2000) private String description;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ContentPackageStatus status = ContentPackageStatus.draft;
 @Column(nullable=false) private Integer scriptCount=0; @Column(nullable=false) private Integer videoCount=0; @Column(nullable=false) private Integer imageCount=0; @Column(nullable=false) private Integer leadCount=0;
 @Column(nullable=false) private Long createdBy; @Column(nullable=false) private Instant createdAt=Instant.now(); private Instant updatedAt=Instant.now();
 public Long getId(){return id;} public void setId(Long id){this.id=id;} public String getName(){return name;} public void setName(String name){this.name=name;} public String getDescription(){return description;} public void setDescription(String description){this.description=description;} public ContentPackageStatus getStatus(){return status;} public void setStatus(ContentPackageStatus status){this.status=status;} public Integer getScriptCount(){return scriptCount;} public void setScriptCount(Integer scriptCount){this.scriptCount=scriptCount;} public Integer getVideoCount(){return videoCount;} public void setVideoCount(Integer videoCount){this.videoCount=videoCount;} public Integer getImageCount(){return imageCount;} public void setImageCount(Integer imageCount){this.imageCount=imageCount;} public Integer getLeadCount(){return leadCount;} public void setLeadCount(Integer leadCount){this.leadCount=leadCount;} public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long createdBy){this.createdBy=createdBy;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;} public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant updatedAt){this.updatedAt=updatedAt;}
}
