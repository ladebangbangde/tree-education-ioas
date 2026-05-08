package com.treeeducation.ioas.report;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.lead.*;
import com.treeeducation.ioas.media.assetfile.*;
import com.treeeducation.ioas.media.contentpackage.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;

/** Real-data report APIs; frontend must not derive core business metrics itself. */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Report", description = "媒体产出报表与运营线索报表")
public class ReportController {
    private final ContentPackageRepository packages;
    private final AssetFileRepository files;
    private final LeadRepository leads;

    public ReportController(ContentPackageRepository packages, AssetFileRepository files, LeadRepository leads) {
        this.packages = packages;
        this.files = files;
        this.leads = leads;
    }

    @GetMapping("/media-output")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDIA')")
    @Operation(summary = "媒体产出报表")
    public ApiResponse<ReportDtos.MediaOutput> media() {
        Instant weekStart = LocalDate.now().minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return ApiResponse.ok(new ReportDtos.MediaOutput(
                files.countByFileTypeAndIsDeletedFalse(AssetFileType.script),
                files.countByFileTypeAndIsDeletedFalse(AssetFileType.video),
                files.countByFileTypeAndIsDeletedFalse(AssetFileType.image),
                packages.countByIsDeletedFalseAndCreatedAtGreaterThanEqual(weekStart),
                packages.countByIsDeletedFalseAndCreatedAtGreaterThanEqual(monthStart)));
    }

    @GetMapping("/operator-leads")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    @Operation(summary = "运营线索报表")
    public ApiResponse<ReportDtos.OperatorLead> operator() {
        Instant today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant week = LocalDate.now().minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant();
        return ApiResponse.ok(new ReportDtos.OperatorLead(leads.count(), leads.countByCreatedAtGreaterThanEqual(today),
                leads.countByCreatedAtGreaterThanEqual(week), leads.countByStatus(LeadStatus.unassigned),
                leads.countByStatus(LeadStatus.assigned), leads.countByStatus(LeadStatus.completed)));
    }

    @GetMapping("/operator/by-package")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    @Operation(summary = "按主题包统计线索")
    public ApiResponse<List<ReportDtos.PackageLeadCount>> byPackage() {
        return ApiResponse.ok(packages.findAll().stream()
                .map(p -> new ReportDtos.PackageLeadCount(p.getId(), p.getTopicName(), leads.findByRelatedPackageId(p.getId()).size()))
                .toList());
    }

    @GetMapping("/operator/trend")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    @Operation(summary = "运营线索趋势")
    public ApiResponse<List<ReportDtos.TrendPoint>> trend(@RequestParam(defaultValue = "7") int days) {
        LocalDate start = LocalDate.now().minusDays(Math.max(days, 1) - 1L);
        List<Lead> all = leads.findAll();
        return ApiResponse.ok(start.datesUntil(LocalDate.now().plusDays(1))
                .map(day -> new ReportDtos.TrendPoint(day.toString(), all.stream().filter(l -> LocalDateTime.ofInstant(l.getCreatedAt(), ZoneOffset.UTC).toLocalDate().equals(day)).count()))
                .toList());
    }
}
