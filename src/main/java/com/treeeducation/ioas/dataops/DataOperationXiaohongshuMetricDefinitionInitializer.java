package com.treeeducation.ioas.dataops;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataOperationXiaohongshuMetricDefinitionInitializer implements SmartInitializingSingleton {
    private final JdbcTemplate jdbc;

    public DataOperationXiaohongshuMetricDefinitionInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        seedImageText();
        seedVideo();
    }

    private void seedImageText() {
        String platform = "XIAOHONGSHU";
        String contentType = "IMAGE_TEXT";
        seed(platform, contentType, "OVERVIEW", "view_count", "播放量", "次", 10, true);
        seed(platform, contentType, "OVERVIEW", "like_count", "点赞量", "次", 20, true);
        seed(platform, contentType, "OVERVIEW", "comment_count", "评论量", "次", 30, true);
        seed(platform, contentType, "OVERVIEW", "favorite_count", "收藏量", "次", 40, true);
        seed(platform, contentType, "OVERVIEW", "share_count", "分享量", "次", 45, false);
        seed(platform, contentType, "OVERVIEW", "cover_click_rate", "封面点击率", "%", 50, true);
        seed(platform, contentType, "OVERVIEW", "copy_expand_rate", "文案展开率", "%", 55, false);
        seed(platform, contentType, "OVERVIEW", "comment_enter_rate", "评论进入率", "%", 60, false);
        seed(platform, contentType, "OVERVIEW", "follower_gain", "单帖涨粉量", "人", 70, true);
        seed(platform, contentType, "FLOW_ANALYSIS", "cover_click_rate", "封面点击率", "%", 10, true);
        seed(platform, contentType, "FLOW_ANALYSIS", "copy_expand_rate", "文案展开率", "%", 20, false);
        seed(platform, contentType, "FLOW_ANALYSIS", "comment_enter_rate", "评论进入率", "%", 30, false);
    }

    private void seedVideo() {
        String platform = "XIAOHONGSHU";
        String contentType = "VIDEO";
        seed(platform, contentType, "OVERVIEW", "view_count", "播放量", "次", 10, true);
        seed(platform, contentType, "OVERVIEW", "like_count", "点赞量", "次", 20, true);
        seed(platform, contentType, "OVERVIEW", "comment_count", "评论量", "次", 30, true);
        seed(platform, contentType, "OVERVIEW", "favorite_count", "收藏量", "次", 40, true);
        seed(platform, contentType, "OVERVIEW", "share_count", "分享量", "次", 45, false);
        seed(platform, contentType, "OVERVIEW", "completion_rate", "整体完播率", "%", 50, true);
        seed(platform, contentType, "OVERVIEW", "five_second_completion_rate", "5S完播率", "%", 60, true);
        seed(platform, contentType, "OVERVIEW", "cover_click_rate", "封面点击率", "%", 70, true);
        seed(platform, contentType, "OVERVIEW", "comment_enter_rate", "评论进入率", "%", 80, false);
        seed(platform, contentType, "OVERVIEW", "follower_gain", "单帖涨粉量", "人", 90, true);
        seed(platform, contentType, "FLOW_ANALYSIS", "completion_rate", "整体完播率", "%", 10, true);
        seed(platform, contentType, "FLOW_ANALYSIS", "five_second_completion_rate", "5S完播率", "%", 20, true);
        seed(platform, contentType, "FLOW_ANALYSIS", "cover_click_rate", "封面点击率", "%", 30, true);
        seed(platform, contentType, "FLOW_ANALYSIS", "comment_enter_rate", "评论进入率", "%", 40, false);
    }

    private void seed(String platform, String contentType, String group, String key, String label, String unit, int order, boolean required) {
        jdbc.update("""
                insert into data_operation_metric_definition
                    (platform_code, content_type, metric_group, metric_key, metric_label, metric_unit, display_order, required_flag, enabled)
                values (?, ?, ?, ?, ?, ?, ?, ?, 1)
                on duplicate key update
                    metric_label = values(metric_label),
                    metric_unit = values(metric_unit),
                    display_order = values(display_order),
                    required_flag = values(required_flag),
                    enabled = 1,
                    updated_at = current_timestamp(6)
                """, platform, contentType, group, key, label, unit, order, required ? 1 : 0);
    }
}
