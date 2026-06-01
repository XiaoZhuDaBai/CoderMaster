# CodeMaster OJ Docker 部署指南

## 项目概述

CodeMaster 是一个现代化的在线评测系统（Online Judge），采用 Spring Cloud 微服务架构，支持 AI 辅助题目生成和在线代码评测。

## 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户请求                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Gateway (8080)                             │
│                   API 网关 / 路由 / 认证                         │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌──────────┬──────────┬──────────┬──────────┬───────────┐
        ▼          ▼          ▼          ▼          ▼
    ┌──────┐  ┌────────┐ ┌────────┐  ┌───────┐  ┌─────┐
    │ User │  │Problem │ │Submission│ │  AI   │  │Judge│
    │ 8081 │  │  8082  │ │  8083  │  │ 8084  │  │8085 │
    └──┬───┘  └────┬───┘ └────┬───┘  └───┬───┘  └──┬────┘
       │           │           │          │          │
       └───────────┴───────────┴──────────┴──────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     基础设施服务                                  │
│  ┌────────┐  ┌────────┐  ┌─────────┐  ┌─────────┐             │
│  │  Nacos │  │ Redis  │  │RabbitMQ │  │  MySQL  │             │
│  │ 8848   │  │ 6379   │  │ 5672    │  │  3306   │             │
│  └────────┘  └────────┘  └─────────┘  └─────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

## 服务说明

| 服务 | 端口 | 依赖组件 | 说明 |
|------|------|----------|------|
| Gateway | 8080 | Nacos, Redis | API 网关，统一入口、路由、认证 |
| User | 8081 | MySQL, Redis, Nacos, Mail | 用户注册、登录 |
| Problem | 8082 | MySQL, Redis, RabbitMQ, Nacos | 题目 CRUD |
| Submission | 8083 | MySQL, Redis, RabbitMQ, Nacos | 代码提交 |
| AI | 8084 | MySQL, Redis, RabbitMQ, Nacos | AI 生成题目/题解 |
| Judge | 8085 | Redis, RabbitMQ, Nacos, Docker | 代码执行、沙箱判题 |
| Contest | 8086 | Redis, Nacos | 比赛服务 |
| Nacos | 8848 | - | 服务注册与配置中心 |
| MySQL | 3306 | - | 主数据库 |
| Redis | 6379 | - | 缓存 |
| RabbitMQ | 5672/15672 | - | 消息队列 + 管理界面 |
| Prometheus | 9090 | - | 指标采集 |
| Grafana | 3000 | - | 监控可视化 |

## 快速开始

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 最低配置：2核4G内存

### 1. 克隆项目

```bash
git clone https://github.com/your-username/oj-microservice.git
cd oj-microservice
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env` 文件，配置必要的环境变量：

```env
# MySQL 密码
MYSQL_ROOT_PASSWORD=your-strong-password

# JWT 密钥（建议32位以上）
JWT_SECRET=your-super-secret-jwt-key

# AI 服务 API Key
DEEPSEEK_API_KEY=your-deepseek-api-key
CROSS_VALIDATION_API_KEY=your-qwen-api-key

# 邮件服务
MAIL_HOST=smtp.qq.com
MAIL_USERNAME=your-email@example.com
MAIL_PASSWORD=your-email-auth-code
```

### 3. 部署服务

**Linux/Mac:**

```bash
chmod +x deploy.sh
./deploy.sh deploy
```

**Windows:**

```cmd
deploy.cmd deploy
```

或者直接使用 docker-compose：

```bash
docker-compose up -d --build
```

### 4. 验证部署

```bash
# 查看服务状态
docker-compose ps

# 检查健康状态
./deploy.sh health
```

## 访问地址

| 服务 | 地址 | 默认凭据 |
|------|------|----------|
| API 网关 | http://localhost:8080 | - |
| Nacos 控制台 | http://localhost:8848/nacos | nacos/nacos |
| RabbitMQ | http://localhost:15672 | guest/guest |

## 常用命令

```bash
# 部署/启动
./deploy.sh deploy

# 停止服务
./deploy.sh stop

# 重启服务
./deploy.sh restart

# 查看日志
./deploy.sh logs gateway

# 查看服务状态
./deploy.sh status

# 检查健康状态
./deploy.sh health

# 清理所有数据（危险）
./deploy.sh clean-all
```

## 数据库初始化

数据库初始化脚本位于 `database/init/` 目录：

- `01-oj_user.sql` - 用户数据库
- `02-oj_problem.sql` - 题目数据库
- `03-oj_submission.sql` - 提交数据库

## 生产环境部署

### 1. 服务器要求

- CPU: 4核+
- 内存: 8G+
- 磁盘: 50G+

### 2. 配置优化

```env
# 生产环境使用强密码
MYSQL_ROOT_PASSWORD=<随机生成的强密码>
JWT_SECRET=<随机生成的32位以上密钥>
REDIS_PASSWORD=<随机生成的强密码>
```

### 3. 反向代理配置（Nginx）

```nginx
upstream oj_gateway {
    server localhost:8080;
}

server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://oj_gateway;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 4. HTTPS 配置

```nginx
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/cert.key;

    location / {
        proxy_pass http://oj_gateway;
    }
}
```

## 故障排查

### 1. 服务启动失败

```bash
# 查看所有日志
docker-compose logs

# 查看特定服务日志
docker-compose logs gateway
```

### 2. 容器无法启动

```bash
# 检查容器状态
docker-compose ps -a

# 查看详细日志
docker-compose up
```

### 3. 重置所有数据

```bash
docker-compose down -v
docker-compose up -d --build
```

## 目录结构

```
oj-microservice/
├── ai/                      # AI 服务
│   └── Dockerfile
├── common/                  # 公共模块
├── database/
│   └── init/                # 数据库初始化脚本
│       ├── 01-oj_user.sql
│       ├── 02-oj_problem.sql
│       └── 03-oj_submission.sql
├── deploy.sh                # Linux/Mac 部署脚本
├── deploy.cmd               # Windows 部署脚本
├── docker-compose.yml       # Docker Compose 配置
├── gateway/                 # 网关服务
│   └── Dockerfile
├── judge/                   # 判题服务
│   └── Dockerfile
├── monitoring/              # 监控配置
│   └── prometheus/
├── problem/                 # 题目服务
│   └── Dockerfile
├── submission/              # 提交服务
│   └── Dockerfile
├── user/                    # 用户服务
│   └── Dockerfile
├── .env.example            # 环境变量示例
└── .env.production         # 生产环境配置示例
```
