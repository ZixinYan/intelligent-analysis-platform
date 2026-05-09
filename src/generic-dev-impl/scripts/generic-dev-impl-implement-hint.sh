#!/usr/bin/env bash
set -euo pipefail

REQUIREMENT=""
PROJECT_PATH=""
DOC_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --requirement) REQUIREMENT="$2"; shift 2 ;;
    --project) PROJECT_PATH="$2"; shift 2 ;;
    --doc-file) DOC_FILE="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$REQUIREMENT" ]]; then
  echo "[错误] 缺少 --requirement" >&2
  exit 1
fi

DOC_CONTEXT="（无）"
[[ -n "$DOC_FILE" && -f "$DOC_FILE" ]] && DOC_CONTEXT=$(cat "$DOC_FILE")

section() { printf '\n== %s ==\n' "$1"; }

section "IMPLEMENT_HINT"
cat <<EOF
### 本地实施检查单
1. 确认用户已明确授权修改代码
2. 阅读相关文件、测试、配置与构建脚本
3. 按最小必要原则实施改动
4. 优先复用现有模式与工具函数
5. 补充或更新测试
6. 执行 lint / typecheck / test / build
7. 完成后立即进入 ultrareview

### 需求
- $REQUIREMENT

### 参考文档
$DOC_CONTEXT

### 工程
- ${PROJECT_PATH:-（未提供）}
EOF

section "REACT"
echo "signal: IMPL_READY"
echo "reason: 可以由当前 CC 会话直接开始本地实施"
