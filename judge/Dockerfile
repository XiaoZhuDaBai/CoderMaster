# Multi-stage build for Spring Boot application
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn package -DskipTests -Pprod

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="oj-microservice"
LABEL description="OJ Judge Service"

RUN apk add --no-cache curl tzdata docker-cli

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 添加docker socket支持（用于沙箱容器）
RUN mkdir -p /var/run/docker.sock && chmod 666 /var/run/docker.sock

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8085/actuator/health || exit 1

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "-Xms512m", "-Xmx1024m", "-XX:+UseG1GC", "app.jar"]
