@echo off
REM ==========================================
REM OJ Microservice Docker Deployment Script
REM ==========================================

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "DEPLOY_MODE=all"
set "COMPOSE_CMD="

REM 解析参数
:parse_args
if "%~1"=="" goto run_command
if /i "%~1"=="--mode" (
    set "DEPLOY_MODE=%~2"
    shift
    shift
    goto parse_args
)
if /i "%~1"=="deploy" goto run_deploy
if /i "%~1"=="start" goto run_deploy
if /i "%~1"=="stop" goto run_stop
if /i "%~1"=="restart" goto run_restart
if /i "%~1"=="status" goto run_status
if /i "%~1"=="logs" goto run_logs
if /i "%~1"=="health" goto run_health
if /i "%~1"=="clean" goto run_clean
if /i "%~1"=="clean-all" goto run_clean_all
if /i "%~1"=="help" goto show_help
goto run_deploy

:run_command
goto run_deploy

REM 检查 Docker Compose 命令
:check_compose_cmd
where docker-compose >nul 2>&1
if %errorlevel% equ 0 (
    set "COMPOSE_CMD=docker-compose"
) else (
    docker compose version >nul 2>&1
    if %errorlevel% equ 0 (
        set "COMPOSE_CMD=docker compose"
    ) else (
        echo [ERROR] Docker Compose 未安装
        exit /b 1
    )
)
goto :eof

REM 检查 Docker
:check_docker
echo [STEP] 检查 Docker 环境...
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker 服务未运行，请启动 Docker
    exit /b 1
)
echo [SUCCESS] Docker 环境检查通过
goto :eof

REM 检查环境变量文件
:check_env
echo [STEP] 检查环境变量文件...
if not exist ".env" (
    if exist ".env.example" (
        echo [WARNING] .env 文件不存在，从 .env.example 复制...
        copy ".env.example" ".env"
        echo [WARNING] 请编辑 .env 文件配置必要的环境变量
    ) else (
        echo [ERROR] .env.example 文件也不存在
        exit /b 1
    )
)
echo [SUCCESS] 环境变量文件检查完成
goto :eof

REM 清理
:cleanup
echo [STEP] 清理旧容器...
if "%DEPLOY_MODE%"=="infra" (
    %COMPOSE_CMD% -f docker-compose.infra.yml down --remove-orphans >nul 2>&1
) else if "%DEPLOY_MODE%"=="app" (
    %COMPOSE_CMD% -f docker-compose.app.yml down --remove-orphans >nul 2>&1
) else (
    %COMPOSE_CMD% -f docker-compose.infra.yml down --remove-orphans >nul 2>&1
    %COMPOSE_CMD% -f docker-compose.app.yml down --remove-orphans >nul 2>&1
)
echo [SUCCESS] 清理完成
goto :eof

REM 部署
:run_deploy
call :check_compose_cmd
call :check_docker
call :check_env
call :cleanup

if "%DEPLOY_MODE%"=="infra" (
    echo [STEP] 部署基础设施服务...
    %COMPOSE_CMD% -f docker-compose.infra.yml up -d --build
    echo [SUCCESS] 基础设施服务部署完成!
    echo.
    echo 访问地址:
    echo   - Nacos: http://服务器A_IP:8848/nacos
    echo   - MySQL: 服务器A_IP:3306
    echo   - Redis: 服务器A_IP:6379
    echo   - RabbitMQ: http://服务器A_IP:15672
    echo   - Prometheus: http://服务器A_IP:9090
    echo   - Grafana: http://服务器A_IP:3000
) else if "%DEPLOY_MODE%"=="app" (
    echo [STEP] 部署应用服务...
    %COMPOSE_CMD% -f docker-compose.app.yml up -d --build
    echo [SUCCESS] 应用服务部署完成!
    echo.
    echo 服务端口:
    echo   - Gateway: 8080
    echo   - User: 8081
    echo   - Problem: 8082
    echo   - Submission: 8083
    echo   - AI: 8084
    echo   - Judge: 8085
) else (
    echo [STEP] 部署基础设施服务...
    %COMPOSE_CMD% -f docker-compose.infra.yml up -d --build
    echo [STEP] 部署应用服务...
    %COMPOSE_CMD% -f docker-compose.app.yml up -d --build
    echo [SUCCESS] 全部服务部署完成!
    echo.
    echo 访问地址:
    echo   - API 网关: http://localhost:8080
    echo   - Nacos: http://localhost:8848/nacos
    echo   - RabbitMQ: http://localhost:15672
    echo   - Prometheus: http://localhost:9090
    echo   - Grafana: http://localhost:3000
)
goto :eof

REM 停止
:run_stop
call :check_compose_cmd
echo [STEP] 停止服务...
if "%DEPLOY_MODE%"=="infra" (
    %COMPOSE_CMD% -f docker-compose.infra.yml down
) else if "%DEPLOY_MODE%"=="app" (
    %COMPOSE_CMD% -f docker-compose.app.yml down
) else (
    %COMPOSE_CMD% -f docker-compose.app.yml down
    %COMPOSE_CMD% -f docker-compose.infra.yml down
)
echo [SUCCESS] 服务已停止
goto :eof

REM 重启
:run_restart
call :check_compose_cmd
echo [STEP] 重启服务...
if "%DEPLOY_MODE%"=="infra" (
    %COMPOSE_CMD% -f docker-compose.infra.yml restart %2
) else if "%DEPLOY_MODE%"=="app" (
    %COMPOSE_CMD% -f docker-compose.app.yml restart %2
) else (
    %COMPOSE_CMD% -f docker-compose.app.yml restart %2
    %COMPOSE_CMD% -f docker-compose.infra.yml restart %2
)
echo [SUCCESS] 服务已重启
goto :eof

REM 状态
:run_status
call :check_compose_cmd
echo [STEP] 服务状态:
if "%DEPLOY_MODE%"=="infra" (
    %COMPOSE_CMD% -f docker-compose.infra.yml ps
) else if "%DEPLOY_MODE%"=="app" (
    %COMPOSE_CMD% -f docker-compose.app.yml ps
) else (
    echo === 基础设施服务 ===
    %COMPOSE_CMD% -f docker-compose.infra.yml ps
    echo.
    echo === 应用服务 ===
    %COMPOSE_CMD% -f docker-compose.app.yml ps
)
goto :eof

REM 日志
:run_logs
call :check_compose_cmd
shift
if "%~1"=="" (
    if "%DEPLOY_MODE%"=="infra" (
        %COMPOSE_CMD% -f docker-compose.infra.yml logs -f
    ) else if "%DEPLOY_MODE%"=="app" (
        %COMPOSE_CMD% -f docker-compose.app.yml logs -f
    ) else (
        %COMPOSE_CMD% -f docker-compose.infra.yml logs -f
    )
) else (
    %COMPOSE_CMD% -f docker-compose.app.yml logs -f %1
)
goto :eof

REM 健康检查
:run_health
call :check_compose_cmd
echo [STEP] 检查服务健康状态...
%COMPOSE_CMD% ps --format "table {{.Name}}\t{{.Status}}"
goto :eof

REM 清理
:run_clean
call :check_compose_cmd
call :cleanup
goto :eof

REM 清理所有数据
:run_clean_all
call :check_compose_cmd
echo [WARNING] 此操作将删除所有数据，包括数据库、缓存等！
set /p confirm="确认删除? (yes/no): "
if /i "%confirm%"=="yes" (
    echo [STEP] 正在删除所有数据...
    if "%DEPLOY_MODE%"=="infra" (
        %COMPOSE_CMD% -f docker-compose.infra.yml down -v --remove-orphans
    ) else if "%DEPLOY_MODE%"=="app" (
        %COMPOSE_CMD% -f docker-compose.app.yml down
    ) else (
        %COMPOSE_CMD% -f docker-compose.app.yml down
        %COMPOSE_CMD% -f docker-compose.infra.yml down -v --remove-orphans
    )
    echo [SUCCESS] 所有数据已删除
)
goto :eof

REM 帮助
:show_help
echo OJ Microservice 部署脚本
echo.
echo 用法: deploy.cmd ^<命令^> [选项]
echo.
echo 部署模式:
echo   --mode all    - 全部服务（单服务器部署，默认）
echo   --mode infra  - 仅基础设施服务（服务器 A）
echo   --mode app    - 仅应用服务（服务器 B）
echo.
echo 可用命令:
echo   deploy     - 部署服务
echo   start      - 启动服务
echo   stop       - 停止服务
echo   restart    - 重启服务
echo   status     - 查看服务状态
echo   logs       - 查看日志
echo   health     - 检查服务健康状态
echo   clean      - 清理旧容器
echo   clean-all  - 删除所有数据（危险）
echo   help       - 显示帮助信息
echo.
echo 示例:
echo   deploy.cmd deploy              - 部署全部服务
echo   deploy.cmd deploy --mode infra - 仅部署基础设施
echo   deploy.cmd deploy --mode app   - 仅部署应用服务
echo.
echo 双服务器部署说明:
echo   1. 在服务器 A 执行: deploy.cmd deploy --mode infra
echo   2. 在服务器 B 执行: deploy.cmd deploy --mode app
echo   3. 修改服务器 B 的 .env 中 INFRA_SERVER_HOST 为服务器 A 的 IP
goto :eof

endlocal
