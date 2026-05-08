package com.treeeducation.ioas.media.recyclebin;

import com.treeeducation.ioas.media.assetfile.AssetFileService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Permanently purges files that stayed in recycle bin for seven days. */
@Component
public class AssetRecycleBinScheduler {
    private final AssetFileService assetFileService;

    public AssetRecycleBinScheduler(AssetFileService assetFileService) {
        this.assetFileService = assetFileService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void purgeExpiredFiles() {
        assetFileService.purgeExpired();
    }
}
