#!/usr/bin/env bash
set -euo pipefail

REQUIREMENT=""
PROJECT_PATH=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --requirement) REQUIREMENT="$2"; shift 2 ;;
    --project) PROJECT_PATH="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$REQUIREMENT" ]]; then
  echo "[错误] 缺少 --requirement" >&2
  exit 1
fi

section() { printf '\n== %s ==\n' "$1"; }

LOWER=$(printf '%s' "$REQUIREMENT" | tr '[:upper:]' '[:lower:]')
QUESTIONS=()

if [[ "$LOWER" != *"验收"* ]] && [[ "$LOWER" != *"acceptance"* ]]; then
  QUESTIONS+=("验收标准是什么？")
fi
if [[ "$LOWER" != *"范围"* ]] && [[ "$LOWER" != *"scope"* ]]; then
  QUESTIONS+=("本次改动范围和明确不做的内容是什么？")
fi
if [[ "$LOWER" != *"接口"* ]] && [[ "$LOWER" != *"字段"* ]] && [[ "$LOWER" != *"api"* ]]; then
  QUESTIONS+=("是否涉及接口、字段、数据结构或配置变更？")
fi

section "CLARIFY"
echo "需求：$REQUIREMENT"
if [[ -n "$PROJECT_PATH" ]]; then
  echo "工程：$PROJECT_PATH"
fi
echo ""
echo "### 已知信息"
echo "- 用户给出了待开发需求"
echo ""

if [[ ${#QUESTIONS[@]} -gt 0 ]]; then
  echo "### 缺失信息"
  for q in "${QUESTIONS[@]}"; do
    echo "- $q"
  done
  echo ""
  echo "### 追问清单"
  i=1
  for q in "${QUESTIONS[@]:0:3}"; do
    echo "$i. $q"
    i=$((i+1))
  done
  section "REACT"
  echo "signal: NEED_CLARIFICATION"
  echo "reason: 需求描述缺少实施所需关键信息"
else
  echo "### 缺失信息"
  echo "- 无明显缺口，可进入规格整理"
  section "REACT"
  echo "signal: SPEC_READY"
  echo "reason: 需求已具备进入规格整理的基本信息"
fi
