# OJ微服务系统

## 项目简介

OJ微服务系统是一个完整的在线判题平台，采用微服务架构设计，集成了AI辅助功能，为编程学习和竞赛提供全方位的解决方案。系统支持多种编程语言的代码提交和自动判题，同时具备题目自动生成、题解辅助生成等AI功能。

## 核心特性

### 技术架构
- 微服务架构：基于Spring Cloud Alibaba，采用服务拆分设计
- 容器化部署：Docker容器化，支持云原生部署
- 服务治理：Nacos服务注册与发现
- 分布式缓存：Redis缓存支持
- 消息队列：RabbitMQ异步处理
- 监控体系：Prometheus + Grafana全面监控

### 编程语言支持
- Java
- C++
- C
- Python 3
- JavaScript
- Go

### AI智能功能
- 题目自动生成：AI根据标签、难度、场景生成编程题目
- 题解辅助生成：流式生成详细题解和解题思路
- 测试用例生成：自动生成完整的测试用例
- 思维提示：提供解题思路和算法提示

### 竞赛功能
- 编程竞赛：支持创建和管理编程竞赛
- 实时排行榜：竞赛期间实时更新排名
- 题目管理：竞赛题目组织和权限控制

### 安全与性能
- 沙箱执行：Docker容器隔离执行用户代码
- 资源限制：CPU、内存、时间限制确保安全
- 并发控制：容器池复用提高性能
- JWT认证：安全的用户身份验证

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                          前端应用层                                │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ Web客户端 / 移动客户端                                        │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────┬───────────────────────────────────────────────┘
                      │ HTTP/REST
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                        API网关层                                 │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ Gateway Service (Spring Cloud Gateway)                      │ │
│  │ - 统一入口、路由转发、认证鉴权、限流熔断                      │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────┬───────────────────────────────────────────────┘
                      │ 服务调用
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                        业务服务层                                │
│  ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┐                     │
│  │User │Prob │Subm │Judge│AI   │Cont │Auth │                     │
│  │Svc  │Svc  │Svc  │Svc  │Svc  │Svc  │Svc  │                     │
│  └─────┴─────┴─────┴─────┴─────┴─────┴─────┘                     │
└─────────────────────┬───────────────────────────────────────────────┘
                      │ 数据访问
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                       数据存储层                                 │
│  ┌─────┬─────┬─────┬─────┬─────┐                                 │
│  │MySQL│Redis│Rabit│Nacos│Obj. │                                 │
│  │     │     │MQ   │     │Store│                                 │
│  └─────┴─────┴─────┴─────┴─────┘                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 服务组成

| 服务名称 | 技术栈 | 端口 | 功能描述 |
|---------|--------|------|----------|
| Gateway | Spring Cloud Gateway | 8080 | API网关，统一入口、路由、认证 |
| User Service | Spring Boot | 8081 | 用户管理、登录注册、权限控制 |
| Problem Service | Spring Boot | 8082 | 题目管理、题目列表查询 |
| Submission Service | Spring Boot | 8083 | 代码提交、判题结果查询 |
| Judge Service | Spring Boot + Docker | 8085 | 代码判题、沙箱执行 |
| AI Service | Spring Boot + LangChain4j | 8084 | AI题目生成、题解生成 |
| Contest Service | Spring Boot | 8086 | 竞赛管理、排行榜 |

### 基础设施服务

- Nacos：服务注册与配置中心
- Redis：分布式缓存、会话存储
- RabbitMQ：消息队列、异步处理
- MySQL：关系型数据存储
- Prometheus：指标收集与监控
- Grafana：可视化监控面板

## 快速开始

### 环境要求

- JDK：21+
- Maven：3.6+
- Docker：20.10+
- Docker Compose：2.0+
- Node.js：16+ (前端开发)

### 推荐服务器配置

| 配置类型 | CPU | 内存 | 适用场景 |
|---------|-----|------|----------|
| 开发环境 | 4核 | 8GB | 本地开发、功能测试 |
| 生产环境 | 8核 | 16GB | 中小型生产部署 |
| 高并发 | 16核 | 32GB | 大规模并发使用 |

### 部署步骤

#### 1. 克隆项目
```bash
git clone https://github.com/your-username/oj-microservice.git
cd oj-microservice
```

#### 2. 启动基础设施
```bash
# 启动Nacos、Redis、Prometheus、Grafana
docker-compose up -d nacos redis prometheus grafana

# 可选：启动cAdvisor进行容器监控
docker-compose up -d cadvisor
```

#### 3. 初始化数据库
```bash
# 执行数据库初始化脚本
mysql -u root -p < database/user-database.sql
mysql -u root -p < database/problem-database.sql
mysql -u root -p < database/submission-database.sql
```

#### 4. 配置环境变量
```bash
# 复制配置文件模板
cp gateway/src/main/resources/application.yaml.example gateway/src/main/resources/application.yaml
cp ai-service/src/main/resources/application.yaml.example ai-service/src/main/resources/application.yaml
# ... 为其他服务复制配置文件

# 编辑配置文件，设置数据库连接、Redis连接、AI API密钥等
```

#### 5. 编译和启动服务
```bash
# 编译所有服务
mvn clean compile

# 启动各个微服务（建议按依赖顺序启动）
mvn spring-boot:run -pl gateway
mvn spring-boot:run -pl user-service
mvn spring-boot:run -pl problem-service
mvn spring-boot:run -pl ai-service
mvn spring-boot:run -pl submission-service
mvn spring-boot:run -pl judge-service
mvn spring-boot:run -pl contest-service
```

#### 6. 验证部署
```bash
# 检查服务状态
curl http://localhost:8080/actuator/health

# 访问监控面板
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

## API文档

完整的API文档请参考：[API接口文档](API接口文档.md)

### 核心接口

#### 用户认证
```bash
# 发送验证码
POST /api/user/sendCode?email=user@example.com

# 登录/注册
POST /api/user/loginOrRegister
```

#### AI功能
```bash
# 生成题目
POST /api/problem/generate

# 流式生成题解
POST /api/solution/generate/streaming
```

#### 代码提交
```bash
# 提交代码
POST /api/submissions

# 查询判题结果
GET /api/submissions/{submissionId}
```

## Docker部署

### 单机部署
```bash
# 构建所有服务镜像
docker build -t oj-gateway ./gateway
docker build -t oj-user-service ./user-service
# ... 构建其他服务镜像

# 使用docker-compose启动
docker-compose -f docker-compose.prod.yml up -d
```

### 分布式部署
```bash
# Kubernetes部署
kubectl apply -f k8s/

# 或使用Docker Swarm
docker stack deploy -c docker-compose.swarm.yml oj-stack
```

## 贡献指南

1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

### 开发规范
- 遵循阿里巴巴Java开发规范
- 提交前运行单元测试：`mvn test`
- 代码格式化：`mvn spotless:apply`

## 技术栈

### 后端技术栈
- 框架：Spring Boot 3.2.5, Spring Cloud 2023.0.0
- 服务治理：Spring Cloud Alibaba, Nacos
- 数据库：MySQL 8.0, Redis 7.0
- 消息队列：RabbitMQ
- AI集成：LangChain4j, OpenAI API
- 监控：Micrometer, Prometheus, Grafana
- 安全：JWT, Spring Security

### 前端技术栈
- 框架：React/Vue.js + TypeScript
- 构建工具：Vite/Webpack
- 状态管理：Redux/Vuex
- UI组件：Ant Design/Material-UI

### DevOps
- 容器化：Docker, Docker Compose
- CI/CD：GitHub Actions/Jenkins
- 配置管理：Nacos Config
- 日志聚合：ELK Stack

## 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

## 贡献者

感谢所有为这个项目做出贡献的开发者！

## 联系我们

- 项目主页：https://github.com/your-username/oj-microservice
- 问题反馈：[GitHub Issues](https://github.com/your-username/oj-microservice/issues)
- 邮箱：your-email@example.com

## 致谢

- [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba) - 微服务解决方案
- [LangChain4j](https://github.com/langchain4j/langchain4j) - Java版LangChain
- [Docker](https://www.docker.com/) - 容器化平台
- [Prometheus](https://prometheus.io/) - 监控系统