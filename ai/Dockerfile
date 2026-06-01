# Multi-stage build for Spring Boot application
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn package -DskipTests -Pprod

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="oj-microservice"
LABEL description="OJ AI Service"

RUN apk add --no-cache curl tzdata

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8084/actuator/health || exit 1

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "-Xms512m", "-Xmx1024m", "-XX:+UseG1GC", "app.jar"]
