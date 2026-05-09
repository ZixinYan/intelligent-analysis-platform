#!/usr/bin/env bash
set -euo pipefail

REQUIREMENT=""
PROJECT_PATH=""
ANSWERS_FILE=""
DOC_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --requirement) REQUIREMENT="$2"; shift 2 ;;
    --project) PROJECT_PATH="$2"; shift 2 ;;
    --answers) ANSWERS_FILE="$2"; shift 2 ;;
    --doc-file) DOC_FILE="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$REQUIREMENT" ]]; then
  echo "[错误] 缺少 --requirement" >&2
  exit 1
fi

ANSWERS_CONTEXT="（无）"
[[ -n "$ANSWERS_FILE" && -f "$ANSWERS_FILE" ]] && ANSWERS_CONTEXT=$(cat "$ANSWERS_FILE")
DOC_CONTEXT="（无）"
[[ -n "$DOC_FILE" && -f "$DOC_FILE" ]] && DOC_CONTEXT=$(cat "$DOC_FILE")

section() { printf '\n== %s ==\n' "$1"; }

section "ULTRA_PLAN"
cat <<EOF
### 维度1：架构与边界
- 评估需求是否跨模块、跨层或影响公共抽象

### 维度2：代码定位
- 在 ${PROJECT_PATH:-项目代码库} 中定位受影响的模块、入口、测试与配置

### 维度3：影响评估
- 关注兼容性、回归面、数据结构变化、接口变更与发布风险

### 维度4：方案对比
- 方案 A：最小改动，快速交付
- 方案 B：适度重构，提升可维护性
- 需结合复杂度与风险做最终取舍

### 维度5：交付清单
1. 阅读现有实现与测试
2. 定位需改文件
3. 先改底层依赖，再改上层调用
4. 补测试
5. 跑验证命令

### 需求上下文
- 需求：$REQUIREMENT
- 用户补充：$ANSWERS_CONTEXT
- 参考文档：$DOC_CONTEXT
EOF

section "REACT"
echo "signal: ULTRA_PLAN_READY"
echo "reason: 已生成多维度实现方案，需确认方案并授权修改"
