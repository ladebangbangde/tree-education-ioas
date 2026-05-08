# Database Schema Overview

Flyway migrations live in `src/main/resources/db/migration`. V1-V9 establish the original schema and V10 aligns that schema with the frontend main contract without deleting history.

## Main Tables

- `sys_user`, `sys_role`, `sys_permission`: authentication, frontend roles (`SUPER_ADMIN`, `MEDIA`, `OPERATOR`, `CONSULTANT`) and permissions.
- `operator_profile`: operator dropdown/list source.
- `content_package`: topic package with `package_no`, `topic_name`, `operator_id`, `operator_name`, date folders, `full_path`, `cover_url`, per-type counts and `upload_status`.
- `asset_file`: object-storage metadata with `file_no`, `file_name`, `file_type`, `bucket_name`, `object_key`, preview/thumbnail URLs, upload status and recycle-bin fields.
- `lead_record`: frontend lead center fields including student, target country/major, budget, degree level, source channel and assignment status.
- `ioas_task`: role-aware task rows for media uploads and operator lead generation.
- `audit_log`: immutable operation log for uploads, deletes, restores, purges, package and lead changes.

## Recycle Bin

Files are soft-deleted by setting `is_deleted = 1`, `deleted_at`, `deleted_by`, and `purge_at = deleted_at + 7 days`. `AssetRecycleBinScheduler` scans every hour and permanently removes expired objects and metadata.
