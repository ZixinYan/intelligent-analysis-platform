#!/usr/bin/env bash
set -euo pipefail

REQUIREMENT=""
PROJECT_PATH=""
DOC_FILE=""
REVIEW_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --requirement) REQUIREMENT="$2"; shift 2 ;;
    --project) PROJECT_PATH="$2"; shift 2 ;;
    --doc-file) DOC_FILE="$2"; shift 2 ;;
    --review-file) REVIEW_FILE="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$REQUIREMENT" ]]; then
  echo "[错误] 缺少 --requirement" >&2
  exit 1
fi

DOC_CONTEXT="（无）"
[[ -n "$DOC_FILE" && -f "$DOC_FILE" ]] && DOC_CONTEXT=$(cat "$DOC_FILE")
EXTRA_REVIEW=""
[[ -n "$REVIEW_FILE" && -f "$REVIEW_FILE" ]] && EXTRA_REVIEW=$(cat "$REVIEW_FILE")

section() { printf '\n== %s ==\n' "$1"; }

section "ULTRA_REVIEW"
cat <<EOF
### 审查维度
- 业务正确性
- 边界与异常处理
- 安全与权限
- 性能与可扩展性
- 编译风险与架构一致性

### 评审结果
- BLOCKER：需结合实际变更逐项确认
- MAJOR：重点关注兼容性、遗漏测试、边界场景
- MINOR：代码整洁性、命名一致性、复用机会

### 审查输入
- 需求：$REQUIREMENT
- 项目：${PROJECT_PATH:-（未提供）}
- 参考文档：$DOC_CONTEXT
- 附加评审输入：${EXTRA_REVIEW:-（无）}

### 输出要求
- 列出 BLOCKER / MAJOR / MINOR
- 标记已验证 / 可能存在 / 未确认
- 给出 PASS / ISSUES / CRITICAL 结论
EOF

section "REACT"
echo "signal: CODE_REVIEW_ISSUES"
echo "reason: 已生成评审框架，请结合实际代码变更补全结论"
