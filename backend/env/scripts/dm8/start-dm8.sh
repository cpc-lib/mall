#!/usr/bin/env bash
# 启动 DM8 容器，等待健康检查通过，并按需初始化 schema。
#
# 行为：仅当 6 张必需业务表缺失时才执行 env/sql/dm8/*.sql，表齐全时直接跳过，不触碰已有数据。
# 可通过环境变量覆盖：DM_CONTAINER、DM_HEALTH_TIMEOUT（秒，默认 300）、DM_SYSDBA_PWD。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ENV_DIR/docker-compose.yml}"
DM_CONTAINER="${DM_CONTAINER:-payment-demo-dm8}"
DM_HEALTH_TIMEOUT="${DM_HEALTH_TIMEOUT:-300}"

log() { echo "[start-dm8] $*"; }
die() { echo "[start-dm8] 错误：$*" >&2; exit 1; }

[ -f "$COMPOSE_FILE" ] || die "未找到 compose 文件：$COMPOSE_FILE"

log "启动 DM8 容器（$DM_CONTAINER）..."
docker compose -f "$COMPOSE_FILE" up -d dm8

log "等待 DM8 健康检查通过（最长 ${DM_HEALTH_TIMEOUT}s）..."
elapsed=0
while :; do
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$DM_CONTAINER" 2>/dev/null || echo missing)"
  case "$status" in
    healthy)
      break
      ;;
    unhealthy|exited|dead|missing)
      die "DM8 容器状态异常：$status（查看日志：docker logs $DM_CONTAINER）"
      ;;
  esac
  if [ "$elapsed" -ge "$DM_HEALTH_TIMEOUT" ]; then
    die "等待 DM8 健康检查超时（${DM_HEALTH_TIMEOUT}s），当前状态：$status"
  fi
  sleep 5
  elapsed=$((elapsed + 5))
done

bash "$SCRIPT_DIR/init-dm8-sql.sh" --if-missing

log "DM8 就绪：必需业务表校验通过"
