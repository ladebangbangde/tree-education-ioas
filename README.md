# tree-education-ioas

Tree Education IOAS 是面向 `tree-education-ioas-fronted` main 分支的真实后端。当前版本以“精准契约整改”为目标：角色、字段、状态枚举、接口粒度、Flyway schema 与 OpenAPI 均对齐前端主流程，不再作为 demo/mock 后端维护。

## 技术栈

- Java 17 / Spring Boot 3.x / Maven
- Spring Security + JWT
- MySQL 8 + Flyway
- Redis
- MinIO（开发）/ OSS-compatible provider 预留
- springdoc-openapi：`/swagger-ui.html`、`/v3/api-docs`

## 本地启动

```bash
docker run --name ioas-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=tree_education_ioas -e MYSQL_USER=ioas -e MYSQL_PASSWORD=ioas_password -p 3306:3306 -d mysql:8
docker run --name ioas-redis -p 6379:6379 -d redis:7
docker run --name ioas-minio -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin -p 9000:9000 -p 9001:9001 -d minio/minio server /data --console-address ':9001'
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger：

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/v3/api-docs`

## 默认账号

| 用户名 | 密码 | 角色 | 部门 |
| --- | --- | --- | --- |
| admin | Admin@123456 | SUPER_ADMIN | 管理部 |
| media | Media@123456 | MEDIA | 媒体部 |
| operator | Operator@123456 | OPERATOR | 运营部 |

生产环境必须替换默认密码与 `IOAS_JWT_SECRET`。

## 前端角色与权限

- `SUPER_ADMIN`：全权限。
- `MEDIA`：创建/编辑/删除主题包、分组上传文件、删除/恢复文件、下载/预览、媒体任务、媒体报表。
- `OPERATOR`：资源中心查看、下载/预览、基于主题包创建线索、运营任务、运营报表；禁止上传/删除文件和编辑/删除主题包。
- `CONSULTANT`：本阶段预留。

接口级权限由 Spring Security `@PreAuthorize` 执行，不能只依赖前端按钮隐藏。

## 核心业务契约

1. 新建主题包：`POST /api/v1/media/content/packages`，只接收 `operatorId + topicName`。
2. 上传文件：`POST /api/v1/media/content/packages/{id}/files`，使用 multipart 字段 `scripts`、`videos`、`images`，每类可多文件。
3. 主题包 `fullPath` 由后端按日期、运营人员和主题名称自动生成。
4. 文件不可脱离主题包存在；文件类型只允许 `script / video / image`。
5. 删除主题包会逻辑删除主题包并将其下文件移入回收站，不再要求主题包为空。
6. 删除单文件只影响文件本身，主题包保留，并同步刷新 `scriptCount/videoCount/imageCount/uploadStatus`。
7. 回收站文件保留 7 天；定时任务每小时扫描 `purgeAt <= now()` 的文件并永久清理对象存储与元数据。
8. 线索围绕主题包创建，不再强制绑定单个 `assetFileId`。
9. 任务由主题包创建、文件上传、线索创建等业务事件联动刷新，不只依赖手动 patch。
10. 报表数据全部从 MySQL 真实数据统计。

## 状态枚举

- `ContentPackageStatus`: `pending_upload`, `uploading`, `partial_completed`, `completed`, `deleted`
- `UploadStatus`: `uploading`, `success`, `failed`, `partial_success`, `pending_supplement`
- `LeadStatus`: `unassigned`, `assigned`, `following`, `completed`, `invalid`
- `TaskType`: `media_upload`, `operator_lead_generate`
- `TaskRoleType`: `media`, `operator`
- `MediaTaskStatus`: `uploading`, `success`, `failed`, `partial_success`, `pending_supplement`
- `OperatorTaskStatus`: `pending`, `processing`, `completed`, `overdue`, `rejected`

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `IOAS_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/tree_education_ioas...` | MySQL 连接串 |
| `IOAS_DATASOURCE_USERNAME` | `ioas` | MySQL 用户 |
| `IOAS_DATASOURCE_PASSWORD` | `ioas_password` | MySQL 密码 |
| `IOAS_REDIS_HOST` | `localhost` | Redis Host |
| `IOAS_REDIS_PORT` | `6379` | Redis Port |
| `IOAS_JWT_SECRET` | 开发默认值 | JWT HMAC 密钥，生产必填强密钥 |
| `IOAS_JWT_EXPIRES_MINUTES` | `1440` | token 有效期 |
| `IOAS_MINIO_ENDPOINT` | `http://localhost:9000` | MinIO endpoint |
| `IOAS_MINIO_ACCESS_KEY` | `minioadmin` | MinIO access key |
| `IOAS_MINIO_SECRET_KEY` | `minioadmin` | MinIO secret key |
| `IOAS_STORAGE_BUCKET` | `ioas-assets` | 对象存储 bucket |
| `IOAS_STORAGE_PUBLIC_BASE_URL` | `http://localhost:9000/ioas-assets` | 预览 URL 前缀 |

## Flyway 规则

所有数据库结构变更必须通过 `src/main/resources/db/migration` 管理。V1-V9 保留初始历史；V10 在不破坏历史的前提下对齐前端 main 数据模型。禁止手工改表后不补 migration。

## 接口契约

- 在线：`/swagger-ui.html`、`/v3/api-docs`
- 静态：`docs/openapi/ioas-openapi.yaml`、`docs/openapi/ioas-openapi.json`
- Endpoint 清单：`docs/api/endpoint-list.md`
- Schema 概览：`docs/database/schema-overview.md`

后续任何接口改动必须先更新 OpenAPI，再更新实现；前后端以 OpenAPI 作为唯一契约来源。
