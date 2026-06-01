# CodeMaster OJ - 在线评测系统

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring%20Cloud-2023.0.0-blue" alt="Spring Cloud">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java">
  <img src="https://img.shields.io/badge/Docker-Ready-blue" alt="Docker">
</p>

## 项目简介

CodeMaster 是一个现代化的在线评测系统（Online Judge），采用微服务架构设计，集成 AI 辅助功能，为编程学习和竞赛提供一站式解决方案。系统支持多种编程语言的代码在线评测，具备题目智能生成、题解辅助生成等 AI 功能。

**在线体验**: https://codemaster.xiaozhu.edu.cn

## 核心特性

### 智能评测引擎
- **多语言支持**: Java、C、C++、Python3、JavaScript、Go 等主流语言
- **沙箱隔离**: Docker 容器隔离执行用户代码，确保系统安全
- **资源限制**: 可配置 CPU、内存、时间限制
- **容器池复用**: 高效利用资源，支持高并发评测

### AI 智能助手
- **题目生成**: 根据标签、难度等条件自动生成编程题目
- **题解生成**: 流式输出详细解题思路和代码实现
- **测试用例**: 自动生成完整的测试用例集
- **交叉验证**: 多模型交叉验证确保生成质量

### 技术架构
| 特性 | 技术选型 |
|------|----------|
| 微服务框架 | Spring Cloud Alibaba 2023.0.0 |
| 服务注册 | Nacos 2.2.3 |
| 消息队列 | RabbitMQ 3.12 |
| 缓存 | Redis 7 |
| 数据库 | MySQL 8.0 |
| AI 集成 | LangChain4j + DeepSeek API |
| 监控 | Prometheus + Grafana |

## 系统架构

![](https://cdn.nlark.com/yuque/0/2026/png/62242460/1769677391402-50e4db35-fd7c-4783-a4a4-b4e494494d9c.png)

### 服务组成

| 服务名称 | 技术栈 | 端口 | 功能描述 |
| --- | --- | --- | --- |
| Gateway | Spring Cloud Gateway | 8080 | API网关，统一入口、路由、认证 |
| User Service | Spring Boot | 8081 | 用户管理、登录注册、权限控制 |
| Problem Service | Spring Boot | 8082 | 题目管理、题目列表查询 |
| Submission Service | Spring Boot | 8083 | 代码提交、判题结果查询 |
| Judge Service | Spring Boot + Docker | 8085 | 代码判题、沙箱执行 |
| AI Service | Spring Boot + LangChain4j | 8084 | AI题目生成、题解生成 |

### 基础设施服务
- Nacos：服务注册与配置中心
- Redis：分布式缓存、会话存储
- RabbitMQ：消息队列、异步处理
- MySQL：关系型数据存储
- Prometheus：指标收集与监控
- Grafana：可视化监控面板

### 核心功能流程图

#### 1. 用户登录/退出

![](https://cdn.nlark.com/yuque/0/2026/png/62242460/1769677531338-d4c6dcf0-e229-4f00-9fe6-c28b7658fa67.png)

#### 2. AI 生成题目流程

![](https://cdn.nlark.com/yuque/0/2026/png/62242460/1769677583237-f21645f5-46c7-4173-a886-01ef4f184abd.png)

#### 3. 提交到判题的流程

![](https://cdn.nlark.com/yuque/0/2026/png/62242460/1769677634108-069987af-65c5-481e-9d97-ced68a76cab9.png)

## 项目截图

### 首页

![](assets/image-78d8fde4-d731-479f-bfa4-3bbc5b3f86fe.png)

首页展示系统概览，包括注册用户数、题目总数、提交总数等统计数据，以及热门题目推荐和快捷入口。

### 在线答题

![](assets/37dd149c28ef76129041d59c08d9b21a-ca233dcc-86fe-4f67-b1de-bb25090498f0.png)

在线答题界面，支持多语言代码编辑，实时语法高亮，提供题目描述、示例输入输出和提示信息。

### AI 智能助手

![](assets/2ba561ec74a72b7bffc2e9f9c55e539b-35577552-8721-4b0e-b192-c1890f5faf0a.png)

集成 AI 智能助手，提供题目思路分析、解题提示服务。基于 LangChain4j 框架和 DeepSeek 大模型，实现流式输出，带来流畅的交互体验。

### 监控运维

![](assets/579c0ed312c8ec533db1fa6494610432-6a316798-4e15-4ec0-b793-b30b9666516c.png)

基于 Prometheus + Grafana 构建的可视化监控体系，实时展示判题状态分布、服务调用链追踪、判题成功率、平均判题时长等核心指标。

## 快速部署

### 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 21+ |
| Maven | 3.9+ |
| Docker | 20.10+ |
| Docker Compose | 2.0+ |

### Docker 部署

```bash
# 1. 克隆项目
git clone https://github.com/your-username/oj-microservice.git
cd oj-microservice

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，配置数据库、AI API 等

# 3. 一键部署
chmod +x deploy.sh
./deploy.sh deploy

# 或直接使用 docker-compose
docker-compose up -d --build
```

### 手动部署

```bash
# 1. 编译项目
mvn clean package -DskipTests

# 2. 启动基础设施服务
docker-compose up -d nacos redis rabbitmq mysql

# 3. 初始化数据库
mysql -h localhost -u root -p < database/init/*.sql

# 4. 启动微服务
java -jar gateway/target/gateway.jar &
java -jar user/target/user.jar &
# ... 其他服务
```

## API文档

### 用户认证
```bash
# 发送验证码
POST /api/user/sendCode?email=user@example.com

# 登录/注册
POST /api/user/loginOrRegister
```

### AI功能
```bash
# 生成题目
POST /api/problem/generate

# 流式生成题解
POST /api/solution/generate/streaming
```

### 代码提交
```bash
# 提交代码
POST /api/submissions

# 查询判题结果
GET /api/submissions/{submissionId}
```

## 项目结构

```
oj-microservice/
├── gateway/              # API 网关服务
├── user/                 # 用户服务
├── problem/              # 题目服务
├── submission/           # 提交服务
├── judge/                # 判题服务
├── ai/                   # AI 服务
├── common/               # 公共模块
├── database/
│   └── init/             # 数据库初始化脚本
├── monitoring/           # 监控配置
│   ├── prometheus/
│   └── grafana/
├── docker-compose.yml    # Docker 编排配置
├── deploy.sh             # 部署脚本
└── DEPLOYMENT.md         # 部署文档
```

## 配置说明

### 环境变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| MYSQL_ROOT_PASSWORD | MySQL root 密码 | root123456 |
| REDIS_PASSWORD | Redis 密码 | - |
| RABBITMQ_USERNAME | RabbitMQ 用户名 | guest |
| RABBITMQ_PASSWORD | RabbitMQ 密码 | guest |
| JWT_SECRET | JWT 密钥 | your-secret-key |
| DEEPSEEK_API_KEY | DeepSeek API 密钥 | sk-xxx |
| MAIL_USERNAME | 邮箱账号 | example@qq.com |
| MAIL_PASSWORD | 邮箱授权码 | - |

### 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Gateway | 8080 | API 网关入口 |
| User | 8081 | 用户服务 |
| Problem | 8082 | 题目服务 |
| Submission | 8083 | 提交服务 |
| AI | 8084 | AI 服务 |
| Judge | 8085 | 判题服务 |
| Nacos | 8848 | 注册中心 |
| RabbitMQ | 15672 | 管理界面 |

## 技术栈

### 后端技术栈
- 框架：Spring Boot 3.2.5, Spring Cloud 2023.0.0
- 服务治理：Spring Cloud Alibaba, Nacos
- 数据库：MySQL 8.0, Redis 7.0
- 消息队列：RabbitMQ
- AI集成：LangChain4j, OpenAI API
- 监控：Micrometer, Prometheus, Grafana
- 安全：JWT, Spring Security

## 监控运维

### 访问地址

| 服务 | 地址 | 凭据 |
|------|------|------|
| Gateway | http://localhost:8080 | - |
| Nacos | http://localhost:8848/nacos | nacos/nacos |
| RabbitMQ | http://localhost:15672 | guest/guest |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin/admin |

### 常用运维命令

```bash
# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f gateway

# 重启服务
docker-compose restart judge

# 清理并重建
docker-compose down -v && docker-compose up -d --build
```

## 开源协议

本项目基于 [MIT License](LICENSE) 开源，可以自由使用和修改。

## 联系方式

- **作者**: xiaozhu
- **邮箱**: 1105774747@qq.com
- **问题反馈**: [GitHub Issues](https://github.com/your-username/oj-microservice/issues)

---

**Star ⭐ 支持一下，让更多人看到！**
