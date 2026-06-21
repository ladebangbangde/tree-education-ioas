FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY .mvn/settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src

# Keep the production build in the stable OA permission model:
# - DATA/SUPER_ADMIN/ADMINISTRATIVE are still required by the controller.
# - Remove any temporary report-export auth bypass filter if it exists.
# - Fix Java 17 stream().toList() immutable-list sorting in the daily report export.
RUN rm -f src/main/java/com/treeeducation/ioas/auth/ReportExportAuthenticationFilter.java \
    && sed -i 's/List<ReportRow> rows = loadReportRows(reportDate);/List<ReportRow> rows = new ArrayList<>(loadReportRows(reportDate));/' src/main/java/com/treeeducation/ioas/dataops/report/DataOpsDailyReportExportController.java

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
