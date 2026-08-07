# 拾路派 · 微服务后端

## 项目架构

```
ai-travel-backend/            # 多模块微服务父项目
├── pom.xml                   # 父 POM（统一依赖管理）
├── common/                   # 共享模块（DTO、工具类）
│   └── src/main/java/...
├── travel-service/           # 小程序后端服务（端口 8080）
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/...
└── admin-service/            # 管理后台服务（端口 8081）
    ├── pom.xml
    ├── Dockerfile
    └── src/main/java/...
```

## 技术栈

- Java 17 + Spring Boot 3.2.4
- MyBatis-Plus 3.5.5
- MySQL (共享数据库 `travel_app`)
- JWT 认证（两套独立密钥）
- Maven 多模块构建

## 本地开发

### 启动全部服务

```bash
docker-compose up -d
```

### 单独启动小程序后端

```bash
cd ai-travel-backend
mvn spring-boot:run -pl travel-service -am
```

### 单独启动管理后台

```bash
cd ai-travel-backend
mvn spring-boot:run -pl admin-service -am
```

### 打包单个服务

```bash
# 打包 travel-service
mvn clean package -pl travel-service -am -DskipTests

# 打包 admin-service
mvn clean package -pl admin-service -am -DskipTests
```

## 部署（微信云托管）

每个服务独立构建 Docker 镜像：

```bash
# 构建 travel-service
docker build -t shilupai-travel:latest -f travel-service/Dockerfile .

# 构建 admin-service
docker build -t shilupai-admin:latest -f admin-service/Dockerfile .
```

或在项目根目录用 docker-compose：

```bash
docker-compose up -d
```

## API 路由

| 前缀 | 服务 | 说明 |
|---|---|---|
| `/api/**` | travel-service:8080 | 小程序端 API |
| `/api/admin/**` | admin-service:8081 | 管理后台 API |

## 共享数据库

两个服务共用 MySQL 数据库 `travel_app`，通过不同的 API 路由隔离业务逻辑。
