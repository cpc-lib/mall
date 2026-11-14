#!/usr/bin/env bash
# 执行容器内 DM_SQL_DIR 下的 *.sql（按文件名顺序），用于初始化或重建 DM8 schema。
#
# 用法：
#   bash env/scripts/dm8/init-dm8-sql.sh                # 强制重建（破坏性，会 DROP 后重建所有表）
#   bash env/scripts/dm8/init-dm8-sql.sh --if-missing   # 必需业务表齐全时跳过，缺失时才执行
#
# 可通过环境变量覆盖：DM_CONTAINER、DM_USERNAME、DM_PASSWORD / DM_SYSDBA_PWD、DM_SQL_DIR、DM_BIN_DIR。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Git Bash（MSYS）会把 /tmp/... 等参数自动转换成 Windows 路径，导致容器内路径失效；这里关闭该转换。
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

DM_CONTAINER="${DM_CONTAINER:-payment-demo-dm8}"
DM_SQL_DIR="${DM_SQL_DIR:-/dm8-init}"
DM_BIN_DIR="${DM_BIN_DIR:-/home/dmdba/dmdbms/bin}"
DM_USERNAME="${DM_USERNAME:-SYSDBA}"
DM_PASSWORD="${DM_PASSWORD:-${DM_SYSDBA_PWD:-Cpc2026#@Dm}}"
DM_SERVER="${DM_SERVER:-127.0.0.1:5236}"
# disql 默认跟随容器 POSIX locale，会把 SQL 中的中文误判成非法字符（[-70037] String not integrated）。
# 指定 UTF-8 locale 后 schema.sql 的 COMMENT/INSERT 中文内容才能正确解析。
DM_LOCALE="${DM_LOCALE:-zh_CN.utf8}"

# 与 backend/docs/DAMENG_DM8_OPERATIONS.md 保持一致。
REQUIRED_TABLES=(
  T_PAYMENT_CHANNEL
  T_PAYMENT_APP
  T_ORDER_INFO
  T_PAYMENT_INFO
  T_PRODUCT
  T_REFUND_INFO
)

IF_MISSING=0
for arg in "$@"; do
  case "$arg" in
    --if-missing) IF_MISSING=1 ;;
    -h|--help)
      sed -n '2,9p' "${BASH_SOURCE[0]}"
      exit 0
      ;;
    *)
      echo "未知参数：$arg" >&2
      exit 2
      ;;
  esac
done

# 密码含 # @ 等字符时，必须用双引号包裹；否则 disql 会把 # 当作扩展选项起点、把 @ 当作连接符。
LOGON="${DM_USERNAME}/\"${DM_PASSWORD}\"@${DM_SERVER}"

log() { echo "[init-dm8-sql] $*"; }
die() { echo "[init-dm8-sql] 错误：$*" >&2; exit 1; }

[ "$(docker inspect -f '{{.State.Running}}' "$DM_CONTAINER" 2>/dev/null || echo false)" = "true" ] \
  || die "容器未运行：$DM_CONTAINER（先执行 env/scripts/dm8/start-dm8.sh）"

# 统计必需业务表已存在的数量；查询失败时输出 0。
existing_required_count() {
  local in_list="" t
  for t in "${REQUIRED_TABLES[@]}"; do
    in_list="${in_list:+$in_list,}'$t'"
  done
  local sql="SELECT COUNT(*) AS CNT FROM USER_TABLES WHERE TABLE_NAME IN ($in_list);"
  docker exec -e LANG="$DM_LOCALE" -e LC_ALL="$DM_LOCALE" "$DM_CONTAINER" sh -c "cd '$DM_BIN_DIR' && exec ./disql -S \"\$1\" -e \"\$2\"" _ "$LOGON" "$sql" 2>/dev/null \
    | tr -d '\r' \
    | grep -E '^[[:space:]]*[0-9]+[[:space:]]*$' \
    | head -1 \
    | tr -d ' ' \
    || true
}

# 执行单个 SQL 文件。
# 执行前在容器内去掉整行注释与空行：disql 脚本模式对“语句 + 空行 + 注释”的组合会误报语法错误。
run_sql_file() {
  local file="$1"
  local tmp="/tmp/dm8-init-run.sql"
  local out="" rc=0

  # 注意：不要加 docker exec -i，否则会消费 while 循环的 heredoc 输入。
  out="$(docker exec -e LANG="$DM_LOCALE" -e LC_ALL="$DM_LOCALE" "$DM_CONTAINER" sh -c '
    set -e
    src="$1"; tmp="$2"; bin="$3"; logon="$4"; start="$5"
    grep -v -e "^[[:space:]]*--" -e "^[[:space:]]*$" "$src" > "$tmp"
    cd "$bin"
    ./disql -S "$logon" "$start"
    rc=$?
    rm -f "$tmp"
    exit $rc
  ' _ "$file" "$tmp" "$DM_BIN_DIR" "$LOGON" "\`$tmp" 2>&1)" || rc=$?

  if [ "$rc" -ne 0 ] || printf '%s' "$out" | grep -qE 'has error|\[-[0-9]+\]'; then
    printf '%s\n' "$out" >&2
    die "执行 $file 失败"
  fi
}

if [ "$IF_MISSING" -eq 1 ]; then
  count="$(existing_required_count)"
  if [ "${count:-0}" -eq "${#REQUIRED_TABLES[@]}" ]; then
    log "必需业务表已齐全（${count}/${#REQUIRED_TABLES[@]}），跳过初始化。"
    exit 0
  fi
  log "必需业务表缺失（${count:-0}/${#REQUIRED_TABLES[@]}），开始执行初始化 SQL。"
fi

sql_files="$(docker exec -i "$DM_CONTAINER" sh -c "ls -1 '$DM_SQL_DIR'/*.sql 2>/dev/null | sort" || true)"
[ -n "$sql_files" ] || die "容器 $DM_CONTAINER 内未找到 $DM_SQL_DIR/*.sql"

while IFS= read -r file; do
  [ -n "$file" ] || continue
  log "执行 $file"
  run_sql_file "$file"
done <<EOF
$sql_files
EOF

count="$(existing_required_count)"
if [ "${count:-0}" -ne "${#REQUIRED_TABLES[@]}" ]; then
  die "初始化未完成：必需业务表校验失败（${count:-0}/${#REQUIRED_TABLES[@]}）。"
fi

log "DM8 SQL 初始化完成。"
