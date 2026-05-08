package com.treeeducation.ioas.media.assetfile;

import io.swagger.v3.oas.annotations.media.Schema;

/** Supported media file business types. */
@Schema(description = "素材文件类型：script 文案脚本、video 视频、image 图片")
public enum AssetFileType { script, video, image }
