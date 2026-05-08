package com.treeeducation.ioas.media.contentpackage;

import io.swagger.v3.oas.annotations.media.Schema;

/** Content package lifecycle status. */
@Schema(description = "主题包状态")
public enum ContentPackageStatus { draft, active, archived }
