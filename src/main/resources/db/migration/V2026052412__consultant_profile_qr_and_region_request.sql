ALTER TABLE operator_profile
    ADD COLUMN consultant_qr_bucket_name VARCHAR(100) NULL,
    ADD COLUMN consultant_qr_object_key VARCHAR(500) NULL,
    ADD COLUMN consultant_qr_public_url VARCHAR(1000) NULL,
    ADD COLUMN public_title VARCHAR(120) NULL,
    ADD COLUMN public_bio VARCHAR(500) NULL,
    ADD COLUMN speciality_region_codes VARCHAR(255) NULL,
    ADD COLUMN speciality_region_names VARCHAR(255) NULL;

CREATE TABLE IF NOT EXISTS consultant_region_change_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    consultant_user_id BIGINT NOT NULL,
    consultant_profile_id BIGINT NOT NULL,
    consultant_name VARCHAR(80) NOT NULL,
    current_region_codes VARCHAR(255) NULL,
    current_region_names VARCHAR(255) NULL,
    requested_region_codes VARCHAR(255) NOT NULL,
    requested_region_names VARCHAR(255) NOT NULL,
    reason VARCHAR(1000) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    reviewer_user_id BIGINT NULL,
    reviewer_name VARCHAR(80) NULL,
    review_remark VARCHAR(1000) NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_region_change_consultant_status (consultant_user_id, status),
    KEY idx_region_change_status (status)
);
