# OJ 微服务监控系统

基于 Prometheus 和 Grafana 的资源监控系统，用于监控 OJ 微服务的各项指标。

## 监控内容

### 1. AI 服务监控
- **AI 调用次数**：按类型统计（题目生成、测试用例生成、题解生成）
- **Token 使用量（精确计量）**：Prompt Token、Completion Token、总 Token，**由 LangChain4j ChatModelListener 从 DeepSeek API 响应中精确捕获**
- **AI Token 消耗速率**：实时 token 消耗速率（tokens/秒）
- **AI 单次调用 Token 量分布**：每次 AI 调用平均消耗的 Token 数量
- **AI 调用响应时间**：P50、P95 响应时间
- **AI 调用错误率**：失败调用统计

### 2. 消息队列监控
- **消息发布数量**：按 Exchange 和 Routing Key 统计
- **消息消费数量**：按 Queue 统计
- **消息处理时间**：消息处理耗时
- **消息处理错误**：处理失败的消息数

### 3. 判题系统容器监控
- **容器池状态**：各语言容器池的当前大小、可用数量、使用中数量
- **容器操作统计**：创建、销毁、故障、超时、归还等操作
- **容器获取耗时**：平均获取容器的时间

### 4. 系统资源监控（可选）
- **Docker 容器资源**：CPU、内存、网络、磁盘使用情况（通过 cAdvisor）

## 关于 cAdvisor

**cAdvisor 的作用**：
- 监控 Docker 容器本身的**系统资源使用情况**（CPU、内存、网络 I/O、磁盘 I/O）
- 提供容器级别的详细资源指标
- 帮助发现资源泄漏、优化资源配置

**与 ContainerMetricsService 的区别**：

| 监控项 | ContainerMetricsService | cAdvisor |
|--------|------------------------|----------|
| **监控层面** | 应用层面（容器池管理） | 系统层面（资源使用） |
| **监控内容** | 容器数量、可用性、操作统计 | CPU、内存、网络、磁盘 |
| **用途** | 了解容器池状态和调度情况 | 了解容器资源消耗和性能 |
| **示例指标** | `judge.container.pool.size` | `container_cpu_usage_seconds_total` |

**是否需要 cAdvisor**：
- ✅ **需要**：如果你想监控每个判题容器的 CPU/内存使用情况，检测资源泄漏，优化容器资源配置
- ❌ **不需要**：如果你只关心容器池的管理状态（有多少容器、是否可用），不关心资源消耗细节

**如果不需要 cAdvisor**：
1. 从 `docker-compose.yml` 中移除 `cadvisor` 服务
2. 从 `prometheus.yml` 中移除 `cadvisor` 的抓取配置
3. 从启动命令中移除：`docker-compose up -d prometheus grafana`（不包含 cadvisor）

## 快速开始

### 1. 启动监控服务

```bash
# 启动 Prometheus 和 Grafana（包含 cAdvisor）
docker-compose up -d prometheus grafana cadvisor

# 或者不启动 cAdvisor（如果不需要系统资源监控）
docker-compose up -d prometheus grafana
```

### 2. 访问监控界面

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000
  - 默认用户名：`admin`
  - 默认密码：`admin`

### 3. 配置服务端点

确保各个微服务已启动并暴露了 `/actuator/prometheus` 端点：

- AI 服务: http://localhost:8084/actuator/prometheus
- 判题服务: http://localhost:8085/actuator/prometheus
- 提交服务: http://localhost:8083/actuator/prometheus
- 题目服务: http://localhost:8082/actuator/prometheus

### 4. 查看 Grafana Dashboard

1. 登录 Grafana
2. 导航到 **Dashboards** -> **Browse**
3. 选择 **OJ 微服务监控面板**

## 配置说明

### Prometheus 配置

配置文件位置：`monitoring/prometheus/prometheus.yml`

主要配置项：
- `scrape_interval`: 抓取指标的时间间隔（默认 15 秒）
- `scrape_configs`: 定义要监控的服务列表

### Grafana 配置

- **数据源配置**: `monitoring/grafana/provisioning/datasources/prometheus.yml`
- **Dashboard 配置**: `monitoring/grafana/provisioning/dashboards/dashboard.yml`
- **Dashboard JSON**: `monitoring/grafana/dashboards/oj-monitoring.json`

## 自定义指标

### AI 服务指标

在 `AiMetricsService` 中定义：
- `ai.calls.total`: AI 调用总次数
- `ai.calls.errors`: AI 调用错误次数
- `ai.tokens.total`: Token 使用总量
- `ai.calls.duration`: AI 调用响应时间

### 消息队列指标

在 `RabbitMQMetricsService` 中定义：
- `rabbitmq.messages.published`: 消息发布总数
- `rabbitmq.messages.consumed`: 消息消费总数
- `rabbitmq.messages.errors`: 消息处理错误总数
- `rabbitmq.messages.processing.duration`: 消息处理时间

### 容器监控指标

在 `ContainerMetricsService` 中定义：
- `judge.container.pool.size`: 容器池大小
- `judge.container.pool.available`: 可用容器数
- `judge.container.pool.in_use`: 使用中容器数
- `judge.container.created`: 容器创建总数
- `judge.container.destroyed`: 容器销毁总数
- `judge.container.faults`: 容器故障总数

## 常见问题

### 1. Prometheus 无法抓取指标

检查：
- 服务是否已启动
- 服务端口是否正确
- `/actuator/prometheus` 端点是否可访问
- 防火墙设置

### 2. Grafana 无法连接 Prometheus

检查：
- Prometheus 是否正常运行
- 数据源配置中的 URL 是否正确
- 网络连接是否正常

### 3. 指标数据不更新

检查：
- Prometheus 抓取配置是否正确
- 服务是否正常产生指标
- 查看 Prometheus 的 Targets 页面，确认抓取状态

## 扩展监控

### 添加 RabbitMQ Exporter

如果需要更详细的 RabbitMQ 监控，可以添加 RabbitMQ Exporter：

```yaml
rabbitmq-exporter:
  image: kbudde/rabbitmq-exporter:latest
  container_name: rabbitmq-exporter
  environment:
    - RABBIT_URL=http://rabbitmq:15672
    - RABBIT_USER=vm_xiaozhu
    - RABBIT_PASSWORD=Wlj041007@
  ports:
    - "9419:9419"
  networks:
    - oj-network
```

### 添加告警规则

在 `monitoring/prometheus/alert_rules.yml` 中定义告警规则，然后在 `prometheus.yml` 中启用：

```yaml
rule_files:
  - "alert_rules.yml"
```

## 维护

### 清理旧数据

Prometheus 默认保留 30 天的数据，可以通过以下方式清理：

```bash
# 进入 Prometheus 容器
docker exec -it prometheus sh

# 清理数据（谨慎操作）
rm -rf /prometheus/*
```

### 备份配置

定期备份以下配置文件：
- `monitoring/prometheus/prometheus.yml`
- `monitoring/grafana/dashboards/oj-monitoring.json`
- `monitoring/grafana/provisioning/` 目录下的所有文件

## 参考文档

- [Prometheus 官方文档](https://prometheus.io/docs/)
- [Grafana 官方文档](https://grafana.com/docs/)
- [Micrometer Prometheus](https://micrometer.io/docs/registry/prometheus)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [cAdvisor 文档](https://github.com/google/cadvisor)
