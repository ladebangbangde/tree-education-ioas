package com.treeeducation.ioas.task;

import com.treeeducation.ioas.media.assetfile.AssetFile;
import com.treeeducation.ioas.media.assetfile.AssetFileRepository;
import com.treeeducation.ioas.media.assetfile.AssetFileType;
import com.treeeducation.ioas.media.contentpackage.ContentPackageStatus;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ContentPackageRefreshService {
    private final ContentPackageRepository contentPackageRepository;
    private final AssetFileRepository assetFileRepository;

    public ContentPackageRefreshService(ContentPackageRepository contentPackageRepository,
                                        AssetFileRepository assetFileRepository) {
        this.contentPackageRepository = contentPackageRepository;
        this.assetFileRepository = assetFileRepository;
    }

    public void refresh(Long packageId) {
        contentPackageRepository.findById(packageId).ifPresent(pkg -> {
            List<AssetFile> files = assetFileRepository.findByPackageIdAndIsDeletedFalse(packageId);
            int scripts = 0;
            int videos = 0;
            int images = 0;
            String cover = pkg.getCoverUrl();
            for (AssetFile file : files) {
                if (AssetFileType.script.equals(file.getFileType())) scripts++;
                if (AssetFileType.video.equals(file.getFileType())) videos++;
                if (AssetFileType.image.equals(file.getFileType())) {
                    images++;
                    if (cover == null || cover.isBlank()) cover = file.getPreviewUrl();
                }
            }
            pkg.setScriptCount(scripts);
            pkg.setVideoCount(videos);
            pkg.setImageCount(images);
            pkg.setCoverUrl(cover);
            pkg.setUploadStatus(files.isEmpty() ? ContentPackageStatus.pending_upload : ContentPackageStatus.completed);
            pkg.setUpdatedAt(Instant.now());
            contentPackageRepository.save(pkg);
        });
    }
}
