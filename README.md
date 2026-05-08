# tree-education-ioas

Tree Education IOAS 正式后端第一版，面向 `tree-education-ioas-fronted` 的真实数据入库场景，不再使用 mock/demo 数据。

## 技术栈

- Java 17 / Spring Boot 3.x / Maven
- Spring Security + JWT
- MySQL 8 + Flyway migration
- Redis（预留会话、缓存、限流等基础设施）
- MinIO（开发对象存储）/ OSS provider 接口预留
- springdoc-openapi：`/swagger-ui.html`、`/v3/api-docs`

## 本地启动依赖

```bash
docker run --name ioas-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=tree_education_ioas -e MYSQL_USER=ioas -e MYSQL_PASSWORD=ioas_password -p 3306:3306 -d mysql:8
docker run --name ioas-redis -p 6379:6379 -d redis:7
docker run --name ioas-minio -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin -p 9000:9000 -p 9001:9001 -d minio/minio server /data --console-address ':9001'
mvn spring-boot:run
```

环境变量见 `src/main/resources/application.yml`。默认种子账号：

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| admin | Admin@123456 | ADMIN |
| media | Media@123456 | MEDIA |
| operator | Operator@123456 | OPERATOR |

生产环境必须通过数据库变更或后台能力替换默认密码。

## 数据库版本管理规则

所有结构变更必须进入 `src/main/resources/db/migration`，禁止手工改表后不回写 migration。第一批脚本覆盖 auth/user、operator profile、content package、asset file、lead、task、audit log、种子角色用户与回收站字段。

## 接口契约

- 在线文档：`/swagger-ui.html`、`/v3/api-docs`
- 静态文档：`docs/openapi/ioas-openapi.yaml`、`docs/openapi/ioas-openapi.json`
- 后续接口改动必须先更新 OpenAPI 文档，再更新实现；前端以该文档生成类型与请求代码。

## 核心业务约束

1. 先创建媒体主题包，再上传文件。
2. 上传文件必须选择主题包，文件不能脱离主题包存在。
3. 文件类型限定为 `script` / `video` / `image`。
4. 素材元数据写 MySQL，文件本体写 MinIO/OSS。
5. 删除单文件进入回收站并同步主题包计数。
6. 创建线索必须绑定主题包与文件，并同步生成运营任务。
7. 报表从真实入库数据统计，前端不得自行推导核心业务状态。
