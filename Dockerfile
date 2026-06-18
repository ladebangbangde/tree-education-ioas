FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY .mvn/settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src

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
