ALTER TABLE data_operation_platform_topic
    ADD COLUMN content_type VARCHAR(32) NOT NULL DEFAULT 'IMAGE_TEXT' COMMENT '内容类型：IMAGE_TEXT/VIDEO' AFTER platform_name;

ALTER TABLE data_operation_content
    ADD COLUMN content_type VARCHAR(32) NOT NULL DEFAULT 'IMAGE_TEXT' COMMENT '内容类型：IMAGE_TEXT/VIDEO' AFTER platform_code;

ALTER TABLE data_operation_asset
    ADD COLUMN content_type VARCHAR(32) NOT NULL DEFAULT 'IMAGE_TEXT' COMMENT '内容类型：IMAGE_TEXT/VIDEO' AFTER asset_type,
    ADD COLUMN asset_group VARCHAR(64) NULL COMMENT '截图分组：DOUYIN_OVERVIEW/DOUYIN_OVERVIEW_CHART/DOUYIN_FLOW_ANALYSIS' AFTER content_type;

CREATE INDEX idx_data_operation_platform_topic_type ON data_operation_platform_topic(platform_code, content_type);
CREATE INDEX idx_data_operation_asset_group ON data_operation_asset(platform_topic_id, asset_type, asset_group);
