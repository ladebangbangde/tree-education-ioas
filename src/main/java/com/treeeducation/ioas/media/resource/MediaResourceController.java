package com.treeeducation.ioas.media.resource;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.PageResponse;
import com.treeeducation.ioas.media.assetfile.AssetFileType;
import com.treeeducation.ioas.media.contentpackage.*;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Shared media resource center APIs. */
@RestController
@RequestMapping("/api/v1/media/resources")
@Tag(name = "Media Resource Center", description = "共享文件池目录树、主题包筛选、按类型查询")
public class MediaResourceController {
    private final ContentPackageService packages;
    private final OperatorProfileRepository operators;

    public MediaResourceController(ContentPackageService packages, OperatorProfileRepository operators) {
        this.packages = packages;
        this.operators = operators;
    }

    @Schema(description = "资源目录节点")
    public record TreeNode(String key, String title, String nodeType, Long operatorId, Long packageId,
                           AssetFileType fileType, List<TreeNode> children) {}

    @GetMapping("/tree")
    @Operation(summary = "资源中心目录树：运营人 -> 主题包 -> script/video/image")
    public ApiResponse<List<TreeNode>> tree() {
        List<ContentPackageDtos.Response> allPackages = packages.list(null, null, null, null,
                new com.treeeducation.ioas.auth.UserPrincipal(0L, "system", "system", "SUPER_ADMIN", "system"));
        return ApiResponse.ok(operators.findByEnabledTrueOrderByNameAsc().stream().map(o -> {
            List<TreeNode> packageNodes = allPackages.stream()
                    .filter(p -> o.getId().equals(p.operatorId()))
                    .map(p -> new TreeNode("package-" + p.id(), p.topicName(), "package", o.getId(), p.id(), null,
                            List.of(new TreeNode("package-" + p.id() + "-script", "脚本", "fileType", o.getId(), p.id(), AssetFileType.script, List.of()),
                                    new TreeNode("package-" + p.id() + "-video", "视频", "fileType", o.getId(), p.id(), AssetFileType.video, List.of()),
                                    new TreeNode("package-" + p.id() + "-image", "图片", "fileType", o.getId(), p.id(), AssetFileType.image, List.of()))))
                    .toList();
            return new TreeNode("operator-" + o.getId(), o.getName(), "operator", o.getId(), null, null, packageNodes);
        }).toList());
    }

    @GetMapping("/packages")
    @Operation(summary = "资源中心主题包卡片列表")
    public ApiResponse<PageResponse<ContentPackageDtos.Response>> packageCards(@RequestParam(required = false) String keyword,
                                                                               @RequestParam(required = false) Long operatorId,
                                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                                               @RequestParam(defaultValue = "20") int pageSize) {
        var principal = new com.treeeducation.ioas.auth.UserPrincipal(0L, "system", "system", "SUPER_ADMIN", "system");
        return ApiResponse.ok(PageResponse.of(packages.list(keyword, operatorId, null, null, principal), pageNum, pageSize));
    }
}
