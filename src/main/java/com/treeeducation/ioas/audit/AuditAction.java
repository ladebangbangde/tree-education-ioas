package com.treeeducation.ioas.audit;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "审计动作")
public enum AuditAction {
    upload_asset, delete_asset, restore_asset, purge_asset, create_lead, update_lead,
    create_package, update_package, delete_package, update_task
}
