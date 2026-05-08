package com.treeeducation.ioas.media.contentpackage;

import io.swagger.v3.oas.annotations.media.Schema;

/** Frontend upload lifecycle status for a content package. */
@Schema(description = "主题包上传状态")
public enum ContentPackageStatus {
    pending_upload, uploading, partial_completed, completed, deleted
}
