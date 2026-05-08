package com.treeeducation.ioas.report;

import io.swagger.v3.oas.annotations.media.Schema;

/** Report DTOs computed from persisted data. */
public final class ReportDtos {
    private ReportDtos() {}

    @Schema(description = "媒体产出报表")
    public record MediaOutput(long scriptTotal, long videoTotal, long imageTotal, long weekPackageCount, long monthPackageCount) {}

    @Schema(description = "运营线索报表")
    public record OperatorLead(long leadTotal, long todayNew, long weekNew, long unassigned, long assigned, long completed,
                               long unassignedCount, long assignedCount, long completedCount) {}

    @Schema(description = "按主题包统计线索")
    public record PackageLeadCount(Long relatedPackageId, String topicName, long count) {}

    @Schema(description = "运营趋势点")
    public record TrendPoint(String date, long count) {}
}
