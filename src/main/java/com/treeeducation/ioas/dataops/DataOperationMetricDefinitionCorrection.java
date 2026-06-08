package com.treeeducation.ioas.dataops;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOn("dataOperationMetricService")
public class DataOperationMetricDefinitionCorrection {
    private final JdbcTemplate jdbc;

    public DataOperationMetricDefinitionCorrection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void correctDefinitions() {
        safeUpdate("""
                update data_operation_metric_definition
                set enabled = 0, updated_at = current_timestamp(6)
                where platform_code = 'DOUYIN'
                  and content_type = 'VIDEO'
                  and metric_group = 'OVERVIEW'
                  and metric_key in ('five_second_completion_rate', 'copy_expand_rate')
                """);

        safeUpdate("""
                delete mv
                from data_operation_metric_value mv
                join data_operation_platform_topic t on t.id = mv.platform_topic_id
                where t.content_type is not null
                  and t.content_type <> ''
                  and mv.content_type <> t.content_type
                """);

        safeUpdate("""
                delete mv
                from data_operation_metric_value mv
                join data_operation_platform_topic t on t.id = mv.platform_topic_id
                where t.content_type = 'VIDEO'
                  and mv.metric_group = 'OVERVIEW'
                  and mv.metric_key in ('five_second_completion_rate', 'copy_expand_rate')
                """);
    }

    private void safeUpdate(String sql) {
        try {
            jdbc.update(sql);
        } catch (RuntimeException ignored) {
        }
    }
}
