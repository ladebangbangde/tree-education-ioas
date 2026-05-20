package com.treeeducation.ioas.media.assetfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AssetFileRepository extends JpaRepository<AssetFile, Long> {
    List<AssetFile> findByIsDeletedFalse();
    List<AssetFile> findByPackageIdAndIsDeletedFalse(Long packageId);
    List<AssetFile> findByPackageId(Long packageId);
    List<AssetFile> findByIsDeletedTrue();
    List<AssetFile> findByIsDeletedTrueAndPurgeAtLessThanEqual(Instant now);
    List<AssetFile> findByBucketNameAndObjectKey(String bucketName, String objectKey);
    long countByFileTypeAndIsDeletedFalse(AssetFileType fileType);
}
