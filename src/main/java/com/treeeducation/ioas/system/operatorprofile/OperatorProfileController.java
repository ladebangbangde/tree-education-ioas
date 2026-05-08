package com.treeeducation.ioas.system.operatorprofile;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/** Operator profile APIs. */
@RestController
@RequestMapping("/api/v1/operators")
@Tag(name = "Operator Profile", description = "运营人员下拉与列表")
public class OperatorProfileController {
    private final OperatorProfileRepository repo;

    public OperatorProfileController(OperatorProfileRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/options")
    @Operation(summary = "运营人员下拉")
    public ApiResponse<List<OperatorProfileDtos.Response>> options() {
        return ApiResponse.ok(repo.findByEnabledTrueOrderByNameAsc().stream().map(OperatorProfileDtos::of).toList());
    }

    @GetMapping
    @Operation(summary = "运营人员列表，支持 name 模糊查询")
    public ApiResponse<PageResponse<OperatorProfileDtos.Response>> list(@RequestParam(required = false) String name,
                                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                                        @RequestParam(defaultValue = "20") int pageSize) {
        List<OperatorProfileDtos.Response> items = repo.findAll().stream()
                .filter(o -> name == null || o.getName().contains(name))
                .sorted(Comparator.comparing(OperatorProfile::getName))
                .map(OperatorProfileDtos::of)
                .toList();
        return ApiResponse.ok(PageResponse.of(items, pageNum, pageSize));
    }
}
