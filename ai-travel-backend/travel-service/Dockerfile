# ============================================
# Travel Service - Dockerfile
# 构建上下文: ai-travel-backend/ 父目录
# ============================================

FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

# 复制 POM 文件（分层利用缓存）
COPY pom.xml .
COPY common/pom.xml common/
COPY travel-service/pom.xml travel-service/

# 下载依赖
RUN mvn dependency:go-offline -pl travel-service -am -B || true

# 复制源码
COPY common/ common/
COPY travel-service/ travel-service/

# 打包 travel-service（含公共模块）
RUN mvn clean package -pl travel-service -am -DskipTests -B

# 运行阶段
FROM eclipse-temurin:17-jre-alpine
ENV TZ=Asia/Shanghai
ENV LANG=C.UTF-8
WORKDIR /
COPY --from=build /workspace/travel-service/target/travel-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
