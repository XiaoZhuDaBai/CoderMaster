#!/bin/bash

# ==========================================
# OJ Microservice Docker Deployment Script
# ==========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 目录定义
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

# 部署模式
DEPLOY_MODE="all"  # all | infra | app

echo_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

echo_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

echo_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# 检查 Docker 是否安装
check_docker() {
    echo_step "检查 Docker 环境..."
    if ! command -v docker &> /dev/null; then
        echo_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        echo_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi
    
    # 检查 Docker 服务是否运行
    if ! docker info &> /dev/null; then
        echo_error "Docker 服务未运行，请启动 Docker"
        exit 1
    fi
    
    echo_success "Docker 环境检查通过"
}

# 检查环境变量文件
check_env_file() {
    echo_step "检查环境变量文件..."
    
    if [ ! -f "$PROJECT_DIR/.env" ]; then
        if [ -f "$PROJECT_DIR/.env.example" ]; then
            echo_warning ".env 文件不存在，从 .env.example 复制..."
            cp "$PROJECT_DIR/.env.example" "$PROJECT_DIR/.env"
            echo_warning "请编辑 .env 文件配置必要的环境变量"
        else
            echo_error ".env.example 文件也不存在"
            exit 1
        fi
    fi
    
    echo_success "环境变量文件检查完成"
}

# 清理旧容器
cleanup() {
    echo_step "清理旧容器..."
    cd "$PROJECT_DIR"
    
    # 根据模式清理
    case "$DEPLOY_MODE" in
        infra)
            docker-compose -f docker-compose.infra.yml down --remove-orphans 2>/dev/null || true
            ;;
        app)
            docker-compose -f docker-compose.app.yml down --remove-orphans 2>/dev/null || true
            ;;
        all)
            docker-compose -f docker-compose.infra.yml down --remove-orphans 2>/dev/null || true
            docker-compose -f docker-compose.app.yml down --remove-orphans 2>/dev/null || true
            ;;
    esac
    
    echo_success "清理完成"
}

# 构建并启动基础设施服务
deploy_infra() {
    echo_step "开始构建和部署基础设施服务..."
    cd "$PROJECT_DIR"
    docker-compose -f docker-compose.infra.yml up -d --build
    echo_success "基础设施服务部署完成"
}

# 构建并启动应用服务
deploy_app() {
    echo_step "开始构建和部署应用服务..."
    cd "$PROJECT_DIR"
    docker-compose -f docker-compose.app.yml up -d --build
    echo_success "应用服务部署完成"
}

# 部署
deploy() {
    case "$DEPLOY_MODE" in
        infra)
            check_docker
            check_env_file
            cleanup
            deploy_infra
            echo ""
            echo_success "基础设施服务部署完成！"
            echo "访问地址:"
            echo "  - Nacos: http://服务器A_IP:8848/nacos (nacos/nacos)"
            echo "  - MySQL: 服务器A_IP:3306"
            echo "  - Redis: 服务器A_IP:6379"
            echo "  - RabbitMQ: http://服务器A_IP:15672"
            echo "  - Prometheus: http://服务器A_IP:9090"
            echo "  - Grafana: http://服务器A_IP:3000 (admin/admin)"
            ;;
        app)
            check_docker
            check_env_file
            cleanup
            deploy_app
            echo ""
            echo_success "应用服务部署完成！"
            echo "服务端口:"
            echo "  - Gateway: 8080"
            echo "  - User: 8081"
            echo "  - Problem: 8082"
            echo "  - Submission: 8083"
            echo "  - AI: 8084"
            echo "  - Judge: 8085"
            ;;
        all)
            check_docker
            check_env_file
            cleanup
            echo_step "部署基础设施服务..."
            deploy_infra
            echo_step "部署应用服务..."
            deploy_app
            echo ""
            echo_success "全部服务部署完成！"
            echo ""
            echo "访问地址:"
            echo "  - API 网关: http://localhost:8080"
            echo "  - Nacos: http://localhost:8848/nacos (nacos/nacos)"
            echo "  - RabbitMQ: http://localhost:15672 (admin/admin123)"
            echo "  - Prometheus: http://localhost:9090"
            echo "  - Grafana: http://localhost:3000 (admin/admin)"
            ;;
    esac
}

# 查看服务状态
status() {
    echo_step "服务状态:"
    cd "$PROJECT_DIR"
    
    case "$DEPLOY_MODE" in
        infra)
            docker-compose -f docker-compose.infra.yml ps
            ;;
        app)
            docker-compose -f docker-compose.app.yml ps
            ;;
        all)
            echo -e "${BLUE}=== 基础设施服务 ===${NC}"
            docker-compose -f docker-compose.infra.yml ps
            echo ""
            echo -e "${BLUE}=== 应用服务 ===${NC}"
            docker-compose -f docker-compose.app.yml ps
            ;;
    esac
}

# 查看日志
logs() {
    cd "$PROJECT_DIR"
    case "$DEPLOY_MODE" in
        infra)
            docker-compose -f docker-compose.infra.yml logs -f "$@"
            ;;
        app)
            docker-compose -f docker-compose.app.yml logs -f "$@"
            ;;
        all)
            if [ -z "$1" ]; then
                echo -e "${BLUE}=== 基础设施服务日志 ===${NC}"
                docker-compose -f docker-compose.infra.yml logs -f
            else
                # 尝试在两个配置文件中查找服务
                docker-compose -f docker-compose.infra.yml logs -f "$@" 2>/dev/null || \
                docker-compose -f docker-compose.app.yml logs -f "$@"
            fi
            ;;
    esac
}

# 停止服务
stop() {
    echo_step "停止服务..."
    cd "$PROJECT_DIR"
    
    case "$DEPLOY_MODE" in
        infra)
            docker-compose -f docker-compose.infra.yml down
            ;;
        app)
            docker-compose -f docker-compose.app.yml down
            ;;
        all)
            docker-compose -f docker-compose.app.yml down
            docker-compose -f docker-compose.infra.yml down
            ;;
    esac
    echo_success "服务已停止"
}

# 重启服务
restart() {
    echo_step "重启服务..."
    cd "$PROJECT_DIR"
    
    case "$DEPLOY_MODE" in
        infra)
            docker-compose -f docker-compose.infra.yml restart "$@"
            ;;
        app)
            docker-compose -f docker-compose.app.yml restart "$@"
            ;;
        all)
            docker-compose -f docker-compose.app.yml restart "$@"
            docker-compose -f docker-compose.infra.yml restart "$@"
            ;;
    esac
    echo_success "服务已重启"
}

# 查看健康状态
health() {
    echo_step "检查服务健康状态..."
    cd "$PROJECT_DIR"
    
    infra_services=("oj-nacos" "oj-mysql" "oj-redis" "oj-rabbitmq" "oj-prometheus" "oj-grafana")
    app_services=("oj-gateway" "oj-user" "oj-problem" "oj-submission" "oj-ai" "oj-judge")
    
    check_service() {
        local svc=$1
        if docker ps --format '{{.Names}}' | grep -q "^${svc}$"; then
            status=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "unknown")
            if [ "$status" = "healthy" ]; then
                echo -e "${GREEN}✓${NC} $svc: healthy"
            elif [ "$status" = "" ]; then
                running=$(docker inspect --format='{{.State.Running}}' "$svc" 2>/dev/null || echo "false")
                if [ "$running" = "true" ]; then
                    echo -e "${YELLOW}◐${NC} $svc: running (no health check)"
                else
                    echo -e "${RED}✗${NC} $svc: not running"
                fi
            else
                echo -e "${RED}✗${NC} $svc: $status"
            fi
        else
            echo -e "${RED}✗${NC} $svc: not found"
        fi
    }
    
    case "$DEPLOY_MODE" in
        infra)
            for svc in "${infra_services[@]}"; do
                check_service "$svc"
            done
            ;;
        app)
            for svc in "${app_services[@]}"; do
                check_service "$svc"
            done
            ;;
        all)
            echo -e "${BLUE}=== 基础设施服务 ===${NC}"
            for svc in "${infra_services[@]}"; do
                check_service "$svc"
            done
            echo ""
            echo -e "${BLUE}=== 应用服务 ===${NC}"
            for svc in "${app_services[@]}"; do
                check_service "$svc"
            done
            ;;
    esac
}

# 清理所有数据（危险操作）
clean_all() {
    echo_warning "此操作将删除所有数据，包括数据库、缓存等！"
    read -p "确认删除? (yes/no): " confirm
    if [ "$confirm" = "yes" ]; then
        echo_step "正在删除所有数据..."
        cd "$PROJECT_DIR"
        
        case "$DEPLOY_MODE" in
            infra)
                docker-compose -f docker-compose.infra.yml down -v --remove-orphans
                ;;
            app)
                docker-compose -f docker-compose.app.yml down
                ;;
            all)
                docker-compose -f docker-compose.app.yml down
                docker-compose -f docker-compose.infra.yml down -v --remove-orphans
                ;;
        esac
        echo_success "所有数据已删除"
    else
        echo "操作已取消"
    fi
}

# 显示帮助信息
show_help() {
    echo "OJ Microservice 部署脚本"
    echo ""
    echo "用法: $0 <命令> [选项]"
    echo ""
    echo "部署模式:"
    echo "  --mode all    - 全部服务（单服务器部署，默认）"
    echo "  --mode infra  - 仅基础设施服务（服务器 A）"
    echo "  --mode app    - 仅应用服务（服务器 B）"
    echo ""
    echo "可用命令:"
    echo "  deploy     - 部署服务"
    echo "  start      - 启动服务"
    echo "  stop       - 停止服务"
    echo "  restart    - 重启服务"
    echo "  status     - 查看服务状态"
    echo "  logs       - 查看日志 (可选: 服务名)"
    echo "  health     - 检查服务健康状态"
    echo "  clean      - 清理旧容器"
    echo "  clean-all  - 删除所有数据（危险）"
    echo "  help       - 显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 deploy                    # 部署全部服务"
    echo "  $0 deploy --mode infra        # 仅部署基础设施"
    echo "  $0 deploy --mode app          # 仅部署应用服务"
    echo "  $0 logs gateway               # 查看网关日志"
    echo "  $0 restart ai                 # 重启AI服务"
    echo ""
    echo "双服务器部署说明:"
    echo "  1. 在服务器 A 执行: ./deploy.sh deploy --mode infra"
    echo "  2. 在服务器 B 执行: ./deploy.sh deploy --mode app"
    echo "  3. 修改服务器 B 的 .env 文件中 INFRA_SERVER_HOST 为服务器 A 的 IP"
}

# 主入口
main() {
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --mode)
                DEPLOY_MODE="$2"
                shift 2
                ;;
            deploy|start)
                CMD="$1"
                shift
                # 处理剩余参数
                while [[ $# -gt 0 ]]; do
                    case "$1" in
                        --mode)
                            DEPLOY_MODE="$2"
                            shift 2
                            ;;
                        *)
                            shift
                            ;;
                    esac
                done
                $CMD
                exit 0
                ;;
            stop|restart|status|health|clean|clean-all|help)
                CMD="$1"
                shift
                $CMD "$@"
                exit 0
                ;;
            logs)
                shift
                logs "$@"
                exit 0
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                echo_error "未知命令: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 默认执行部署
    deploy
}

main "$@"
