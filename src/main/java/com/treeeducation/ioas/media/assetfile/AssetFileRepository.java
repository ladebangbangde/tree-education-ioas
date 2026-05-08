package com.treeeducation.ioas.media.assetfile;
import org.springframework.data.domain.Page;import org.springframework.data.domain.Pageable;import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;
public interface AssetFileRepository extends JpaRepository<AssetFile,Long>{
 Page<AssetFile> findByIsDeletedFalse(Pageable pageable); Page<AssetFile> findByPackageIdAndIsDeletedFalse(Long packageId, Pageable pageable); Page<AssetFile> findByTypeAndIsDeletedFalse(AssetFileType type, Pageable pageable); List<AssetFile> findByPackageIdAndIsDeletedFalse(Long packageId); Page<AssetFile> findByIsDeletedTrue(Pageable pageable);
}
