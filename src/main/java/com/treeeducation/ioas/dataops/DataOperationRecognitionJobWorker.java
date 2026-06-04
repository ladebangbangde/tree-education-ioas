package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.recognition.ImageRecognitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DataOperationRecognitionJobWorker {
    private static final Logger log = LoggerFactory.getLogger(DataOperationRecognitionJobWorker.class);

    private final JdbcTemplate jdbc;
    private final ImageRecognitionService recognitionService;

    public DataOperationRecognitionJobWorker(JdbcTemplate jdbc, ImageRecognitionService recognitionService) {
        this.jdbc = jdbc;
        this.recognitionService = recognitionService;
    }

    @Scheduled(fixedDelayString = "${data-ops.recognition-worker.fixed-delay-ms:15000}")
    public void runOnce() {
        List<Map<String, Object>> assets = jdbc.queryForList("""
                select id, platform_topic_id, asset_type
                from data_operation_asset
                where asset_type in ('COVER','DATA_SCREENSHOT')
                  and (upload_status is null or upload_status in ('success','pending'))
                order by id asc
                limit 5
                """);
        for (Map<String, Object> asset : assets) {
            Long assetId = numberToLong(asset.get("id"));
            if (assetId == null) continue;
            try {
                recognitionService.recognizeDataAsset(assetId, null, "CONTENT_DETAIL");
            } catch (RuntimeException ex) {
                log.warn("data operation asset recognition failed, assetId={}, message={}", assetId, ex.getMessage());
                try {
                    jdbc.update("update data_operation_asset set upload_status = 'failed' where id = ?", assetId);
                } catch (RuntimeException updateError) {
                    log.warn("mark asset recognition failed status error, assetId={}, message={}", assetId, updateError.getMessage());
                }
            }
        }
    }

    private Long numberToLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
