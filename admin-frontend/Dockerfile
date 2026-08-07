# ============================================
# Admin Frontend - Dockerfile
# 拾路派管理后台前端（React + Vite）
# 部署到微信云托管
# ============================================
# 环境变量：
#   ADMIN_API_URL - admin-service 地址
#     默认: http://localhost:8081
#     云托管: http://admin-service（服务名）
# ============================================

# 第一阶段：构建
FROM node:20-alpine AS build
WORKDIR /workspace

# 复制依赖配置
COPY package.json package-lock.json ./

# 安装依赖
RUN npm ci

# 复制源码并构建
COPY . .
RUN npm run build

# 第二阶段：运行
FROM nginx:alpine
ENV TZ=Asia/Shanghai
ENV ADMIN_API_URL=http://localhost:8081

# 复制 nginx 模板
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# 复制构建产物
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
