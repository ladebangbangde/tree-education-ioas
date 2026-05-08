# IOAS API 文档说明

本仓库以 OpenAPI 为唯一接口契约来源：

- 在线：`GET /swagger-ui.html`、`GET /v3/api-docs`
- 静态：`docs/openapi/ioas-openapi.yaml`、`docs/openapi/ioas-openapi.json`

接口覆盖前端优先页面：`/media/content`、`/operator/leads`、`/media-assets`、`/tasks`、`/reports`。

更新规则：任何 Controller、DTO、VO、Enum 或状态码语义变化，必须先更新静态 OpenAPI 文档，再更新实现并确保 springdoc 在线文档一致。
