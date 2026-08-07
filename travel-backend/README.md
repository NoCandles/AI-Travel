# AI 智能旅行规划后端服务

基于 Spring Boot 3.2 的 AI 旅行规划后端服务，提供行程生成等功能。

## 技术栈

- **框架**: Spring Boot 3.2.4
- **数据库**: mysql
- **缓存**: 内存缓存
- **AI**: deepseek

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.9+

### 2. 配置 API Keys

在 `src/main/resources/application.yml` 中配置以下 API Key：

```yaml
deepseek:
  api-key: your-deepseek-api-key
```

### 3. 启动方式



#### 方式1：本地运行

```bash
# 1. 启动 mysql

# 2. 构建并运行应用

### 4. 验证启动

访问：http://localhost:8080/api/health

## API 接口文档

### 行程管理接口

#### 1. 创建行程（AI 生成）

```bash
POST http://localhost:8080/api/trips
Content-Type: application/json
X-User-Id: user123

{
  "destination": "厦门",
  "startDate": "2024-06-01",
  "endDate": "2024-06-03",
  "days": 3,
  "preferences": ["photography", "foodie"],
  "mustVisitPlaces": ["鼓浪屿", "厦门大学"],
  "budget": "mid-range"
}
```

响应：
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "厦门 3 日游",
    "destination": "厦门",
    "days": [...]
  },
  "message": "行程创建成功"
}
```

#### 2. 获取行程详情

```bash
GET http://localhost:8080/api/trips/{id}
X-User-Id: user123
```

#### 3. 获取用户所有行程

```bash
GET http://localhost:8080/api/trips
X-User-Id: user123
```

#### 4. 更新行程

```bash
PUT http://localhost:8080/api/trips/{id}
Content-Type: application/json
X-User-Id: user123

{
  "name": "更新后的行程名称",
  "days": [...]
}
```

#### 5. 删除行程

```bash
DELETE http://localhost:8080/api/trips/{id}
X-User-Id: user123
```

## 前端对接说明

### 1. 基础配置

```javascript
// 前端配置
const API_BASE_URL = 'http://localhost:8080/api';

// 或使用环境变量
const API_BASE_URL = process.env.REACT_APP_API_URL;
```

### 2. 创建行程示例

```javascript
// 使用 fetch
async function createTrip(tripData) {
  const response = await fetch(`${API_BASE_URL}/trips`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userId // 从登录信息中获取
    },
    body: JSON.stringify(tripData)
  });
  
  const result = await response.json();
  
  if (result.success) {
    return result.data;
  } else {
    throw new Error(result.message);
  }
}

// 使用 axios
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// 添加请求拦截器，自动添加 userId
api.interceptors.request.use(config => {
  const userId = localStorage.getItem('userId');
  if (userId) {
    config.headers['X-User-Id'] = userId;
  }
  return config;
});

// 创建行程
const createTrip = async (data) => {
  const { data: response } = await api.post('/trips', data);
  return response;
};
```

## 数据库表结构

### trip_plans (行程计划表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| name | VARCHAR | 行程名称 |
| destination | VARCHAR | 目的地 |
| start_date | DATE | 开始日期 |
| end_date | DATE | 结束日期 |
| description | TEXT | 描述 |
| status | VARCHAR | 状态 |
| user_id | VARCHAR | 用户 ID |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### trip_days (行程天数表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| trip_id | UUID | 行程 ID (外键) |
| day | INTEGER | 第几天 |
| date | DATE | 日期 |
| weather | VARCHAR | 天气 |
| notes | TEXT | 备注 |

### trip_spots (景点表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| trip_day_id | UUID | 天数 ID (外键) |
| name | VARCHAR | 景点名称 |
| category | VARCHAR | 类型 |
| address | VARCHAR | 地址 |
| latitude | DOUBLE | 纬度 |
| longitude | DOUBLE | 经度 |
| order | INTEGER | 顺序 |

## 常见问题

### Q: 如何获取 DeepSeek API Key？

A: 访问 DeepSeek 官网，注册后在控制台创建 API Key。

### Q: 数据库连接失败怎么办？

A: 检查 PostgreSQL 是否启动，端口 5432 是否可访问，用户名密码是否正确。

### Q: 如何查看应用日志？

A: 
```bash
# Docker 方式
docker-compose logs -f app

# 本地运行
tail -f logs/application.log
```

## 开发计划

- [ ] 用户认证与授权 (JWT)
- [ ] 行程分享功能
- [ ] 离线地图支持
- [ ] 多语言支持
- [ ] 性能优化与监控

## 许可证

MIT License

## 联系方式

如有问题，请提交 Issue 或联系开发团队。
