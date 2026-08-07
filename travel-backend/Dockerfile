# 第一阶段：构建阶段 - 使用 Docker Hub 官方镜像
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn clean package -DskipTests


# 第二阶段：运行阶段 - 使用 Docker Hub 官方镜像
FROM eclipse-temurin:17-jre-alpine
ENV TZ=Asia/Shanghai
ENV LANG=C.UTF-8
WORKDIR /
COPY --from=build /workspace/target/travel-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar","--spring.profiles.active=prod"]