# ============================================
# 拾路派微服务 - 多模块 Dockerfile
# 通过 BUILD_SERVICE 构建参数选择服务
# 默认构建 travel-service
# ============================================
# 构建方式：
#   travel-service: docker build --build-arg BUILD_SERVICE=travel-service .
#   admin-service:  docker build --build-arg BUILD_SERVICE=admin-service .
# ============================================

ARG BUILD_SERVICE=travel-service

# ============================================
# 第一阶段：构建
# ============================================
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

ARG BUILD_SERVICE

# 复制所有 POM 文件（父 POM 引用了全部模块，必须都存在）
COPY pom.xml .
COPY common/pom.xml common/
COPY travel-service/pom.xml travel-service/
COPY admin-service/pom.xml admin-service/

# 下载依赖（只下载目标服务的依赖以节省时间）
RUN mvn dependency:go-offline -pl ${BUILD_SERVICE} -am -B || true

# 复制全部公共模块源码
COPY common/ common/

# 复制目标服务的全部源码
COPY ${BUILD_SERVICE}/ ${BUILD_SERVICE}/

# 打包指定服务
RUN mvn clean package -pl ${BUILD_SERVICE} -am -DskipTests -B

# ============================================
# 第二阶段：运行
# ============================================
FROM eclipse-temurin:17-jre-alpine
ENV TZ=Asia/Shanghai
ENV LANG=C.UTF-8
ENV SPRING_PROFILES_ACTIVE=prod
WORKDIR /

ARG BUILD_SERVICE
COPY --from=build /workspace/${BUILD_SERVICE}/target/${BUILD_SERVICE}-1.0.0.jar app.jar

# 默认暴露 8080 端口（admin-service 需改为 8081）
EXPOSE 8080

ENTRYPOINT ["java","-jar","/app.jar"]
