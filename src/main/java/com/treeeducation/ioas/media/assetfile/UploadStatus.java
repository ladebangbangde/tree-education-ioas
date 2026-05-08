package com.treeeducation.ioas.media.assetfile;

import io.swagger.v3.oas.annotations.media.Schema;

/** Upload status shared by files and media upload tasks. */
@Schema(description = "上传状态")
public enum UploadStatus {
    uploading, success, failed, partial_success, pending_supplement
}
