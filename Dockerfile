FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY .mvn/settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src

# Keep the production build in the stable OA permission model:
# - DATA/SUPER_ADMIN/ADMINISTRATIVE are still required by the controller.
# - Remove any temporary report-export auth bypass filter if it exists.
# - Fix Java 17 stream().toList() immutable-list sorting in the daily report export.
# - Prefer sys_user.display_name_zh for OA/DataOps role display names and report names.
# - Hide deleted DataOps packages/topics/contents/assets everywhere users and reports read data.
RUN rm -f src/main/java/com/treeeducation/ioas/auth/ReportExportAuthenticationFilter.java \
    && sed -i 's/List<ReportRow> rows = loadReportRows(reportDate);/List<ReportRow> rows = new ArrayList<>(loadReportRows(reportDate));/' src/main/java/com/treeeducation/ioas/dataops/report/DataOpsDailyReportExportController.java \
    && sed -i "s/select id, username, display_name, department, role_code from sys_user where status = 'ACTIVE'/select id, username, coalesce(nullif(display_name_zh, ''), display_name, username) as display_name, display_name_zh, department, role_code from sys_user where status = 'ACTIVE'/" src/main/java/com/treeeducation/ioas/dataops/DataOperationController.java \
    && sed -i "s/select display_name from sys_user where id in (:ids)/select coalesce(nullif(display_name_zh, ''), display_name, username) from sys_user where id in (:ids)/" src/main/java/com/treeeducation/ioas/dataops/DataOperationController.java \
    && sed -i 's/String.join("+", names)/String.join("、", names)/g' src/main/java/com/treeeducation/ioas/dataops/DataOperationController.java \
    && sed -i "s/select \* from data_operation_topic_package where 1=1/select * from data_operation_topic_package where lower(coalesce(status, '')) <> 'deleted'/" src/main/java/com/treeeducation/ioas/dataops/DataOperationController.java \
    && sed -i "s/select \* from data_operation_topic_package where id = ?/select * from data_operation_topic_package where id = ? and lower(coalesce(status, '')) <> 'deleted'/g" src/main/java/com/treeeducation/ioas/dataops/DataOperationController.java \
    && sed -i "s/select \* from data_operation_platform_topic where package_id = ? order by id desc/select * from data_operation_platform_topic where package_id = ? and lower(coalesce(status, '')) <> 'deleted' order by id desc/g" src/main/java/com/treeeducation/ioas/dataops/DataOperationController.java \
    && sed -i "s/select \* from data_operation_content where package_id = ? order by id desc/select * from data_operation_content where package_id = ? and lower(coalesce(status, '')) <> 'deleted' order by id desc/g" src/main/java/com/treeeducation/ioas/dataops/DataOperationController.java \
    && sed -i "s/select \* from data_operation_asset where package_id = ? order by id desc/select a.* from data_operation_asset a left join data_operation_content c on c.id = a.content_id left join data_operation_platform_topic t on t.id = a.platform_topic_id where a.package_id = ? and lower(coalesce(c.status, '')) <> 'deleted' and lower(coalesce(t.status, '')) <> 'deleted' order by a.id desc/g" src/main/java/com/treeeducation/ioas/dataops/DataOperationController.java \
    && sed -i "/where p.topic_date = ?/a\                  and lower(coalesce(p.status, '')) <> 'deleted'\n                  and lower(coalesce(t.status, '')) <> 'deleted'\n                  and lower(coalesce(c.status, '')) <> 'deleted'" src/main/java/com/treeeducation/ioas/dataops/report/DataOpsDailyReportExportController.java

RUN mvn -B -s /root/.m2/settings.xml \
    -Dmaven.wagon.http.retryHandler.count=10 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    clean package -DskipTests

FROM maven:3.9.9-eclipse-temurin-17
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="" \
    IOAS_SERVER_PORT=8080

COPY --from=builder /app/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]