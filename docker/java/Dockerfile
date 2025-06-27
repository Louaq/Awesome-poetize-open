# 使用Maven作为构建环境
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# 复制整个poetize-server目录到容器中
COPY ./poetize-server /app

# 在容器中运行Maven构建
RUN mvn -f /app/pom.xml clean package -DskipTests

# 最终运行环境
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装健康检查所需工具
RUN apk add --no-cache netcat-openbsd curl

# 从构建阶段复制JAR文件
COPY --from=builder /app/poetry-web/target/poetize-server-exec.jar /app/app.jar

# 暴露端口
EXPOSE 8081

# 创建健康检查脚本
RUN echo '#!/bin/sh\nset -e\nif nc -z localhost 8081; then\n  curl -f http://localhost:8081/actuator/health || exit 1\n  exit 0\nfi\nexit 1' > /app/healthcheck.sh && \
    chmod +x /app/healthcheck.sh

# 启动命令
CMD ["sh", "-c", "ls -la /app && java $JAVA_OPTS -jar /app/app.jar"] 