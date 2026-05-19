package com.treeeducation.ioas.auth;

import java.util.List;
import java.util.Map;

public final class PermissionCatalog {
    private PermissionCatalog() {}

    public static final List<String> ALL_ROUTES = List.of(
            "/dashboard",
            "/media/content", "/operator/leads", "/media-assets", "/tasks", "/reports",
            "/leads/list", "/leads/detail/:id", "/leads/assign", "/leads/follow",
            "/students/list", "/students/detail/:id",
            "/applications/kanban", "/applications/detail/:id", "/applications/stage/:stage", "/applications/materials", "/applications/offers", "/applications/visa",
            "/cms/articles", "/cms/cases", "/cms/case/detail/:id", "/cms/case/preview/:id", "/cms/case/edit/:id", "/cms/config/country", "/cms/config/school", "/cms/media", "/cms/site-config", "/cms/:type/:mode/:id", "/cms/config/:mode",
            "/knowledge/library", "/messages/tasks",
            "/reports/overview", "/reports/leads",
            "/settings/users", "/settings/advisors", "/settings/departments", "/settings/positions", "/settings/data-permission", "/settings/menu-permission",
            "/settings/dict/detail/:id", "/settings/dict/edit/:id", "/settings/opLog/detail/:id", "/settings/loginLog/detail/:id", "/settings/:type/:mode/:id", "/settings/data-permission/config", "/settings/roles", "/settings/dicts", "/settings/logs"
    );

    public static final List<String> ALL_ACTIONS = List.of(
            "view", "edit", "export", "assign", "batch", "publish", "preview", "permission", "delete", "resetPassword",
            "follow", "convert", "highIntent", "new", "file", "log", "config", "stage", "advisor", "offline", "more",
            "retry", "generateLead", "upload", "download", "bindOperator", "restore", "createPackage", "editOwnContent", "deleteOwnContent"
    );

    private static final Map<String, List<String>> ROUTES_BY_ROLE = Map.of(
            "SUPER_ADMIN", ALL_ROUTES,
            "MEDIA", List.of("/media/content", "/media-assets", "/tasks", "/reports"),
            "OPERATOR", List.of("/operator/leads", "/media-assets", "/tasks", "/reports"),
            "CONSULTANT", List.of("/dashboard", "/leads/list", "/leads/detail/:id", "/leads/assign", "/leads/follow", "/students/list", "/students/detail/:id", "/applications/kanban", "/applications/detail/:id", "/applications/stage/:stage", "/applications/materials", "/applications/offers", "/applications/visa", "/knowledge/library", "/messages/tasks")
    );

    private static final Map<String, List<String>> ACTIONS_BY_ROLE = Map.of(
            "SUPER_ADMIN", ALL_ACTIONS,
            "MEDIA", List.of("view", "new", "createPackage", "upload", "download", "preview", "edit", "editOwnContent", "delete", "deleteOwnContent", "bindOperator", "retry", "restore", "file"),
            "OPERATOR", List.of("view", "download", "preview", "file", "log", "generateLead", "more"),
            "CONSULTANT", List.of("view", "edit", "assign", "follow", "file", "log", "stage", "advisor", "more")
    );

    public static List<String> routes(String role) {
        return ROUTES_BY_ROLE.getOrDefault(role, List.of());
    }

    public static List<String> actions(String role) {
        return ACTIONS_BY_ROLE.getOrDefault(role, List.of("view"));
    }
}
