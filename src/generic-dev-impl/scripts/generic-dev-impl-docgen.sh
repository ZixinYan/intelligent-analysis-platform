#!/usr/bin/env bash
set -euo pipefail

MODE=""
REQUIREMENT=""
PROJECT_PATH=""
ANSWERS_FILE=""
DOC_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    --requirement) REQUIREMENT="$2"; shift 2 ;;
    --project) PROJECT_PATH="$2"; shift 2 ;;
    --answers) ANSWERS_FILE="$2"; shift 2 ;;
    --doc-file) DOC_FILE="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$MODE" || -z "$REQUIREMENT" ]]; then
  echo "[错误] 缺少 --mode 或 --requirement" >&2
  exit 1
fi

ANSWERS_CONTEXT="（无）"
[[ -n "$ANSWERS_FILE" && -f "$ANSWERS_FILE" ]] && ANSWERS_CONTEXT=$(cat "$ANSWERS_FILE")
DOC_CONTEXT="（无）"
[[ -n "$DOC_FILE" && -f "$DOC_FILE" ]] && DOC_CONTEXT=$(cat "$DOC_FILE")

section() { printf '\n== %s ==\n' "$1"; }

case "$MODE" in
  spec)
    section "SPEC"
    cat <<EOF
### 背景与目标
- 需求：$REQUIREMENT
- 目标：明确用户要解决的问题与交付结果

### 范围
- 纳入本次实现的能力：根据需求与补充信息落地

### 非目标
- 未明确要求的扩展能力不纳入本次实现

### 输入输出/接口影响
- 待确认信息：$ANSWERS_CONTEXT

### 验收标准
- 功能行为与需求描述一致
- 不破坏现有相关能力
- 相关验证命令执行通过

### 风险与待确认点
- 需进一步确认边界条件、兼容性与发布影响
EOF
    section "REACT"
    echo "signal: SPEC_READY"
    echo "reason: 规格文档已生成，等待审批"
    ;;
  implplan)
    section "IMPL_PLAN"
    cat <<EOF
### 技术方案概述
- 基于现有代码结构做最小必要改动，优先复用既有实现模式

### 影响文件/模块
- 需根据项目代码进一步定位实际文件
- 项目路径：${PROJECT_PATH:-（未提供）}

### 数据结构或接口变更
- 根据需求判断是否涉及字段、接口、配置或存储层调整
- 用户补充：$ANSWERS_CONTEXT

### 实施步骤
1. 阅读相关代码与测试
2. 定位受影响文件与依赖
3. 实施最小闭环改动
4. 补充/更新测试
5. 执行 lint、typecheck、test、build

### 测试与验证方案
- 单元/集成/手工验证按项目现状执行
- 覆盖正常路径、边界条件、回归场景

### 风险与回滚思路
- 风险：改动范围识别不足、兼容性遗漏、测试覆盖不足
- 回滚：按提交粒度回滚相关文件改动

### 参考规格
$DOC_CONTEXT
EOF
    section "REACT"
    echo "signal: IMPL_DOC_READY"
    echo "reason: 实现方案已生成，需用户授权后修改代码"
    ;;
  perf)
    section "PERF"
    cat <<EOF
### 性能目标
- 需求：$REQUIREMENT

### 重点排查维度
- 重复计算
- 大量 I/O 或无分页读取
- 低效查询/遍历
- 不必要的同步阻塞
- 缓存与并发问题

### 建议分析步骤
1. 定位核心调用链
2. 找出热点路径
3. 识别可量化瓶颈
4. 提出低风险优化顺序

### 补充上下文
- 用户补充：$ANSWERS_CONTEXT
- 规格文档：$DOC_CONTEXT
EOF
    section "REACT"
    echo "signal: PERF_ANALYSIS_DONE"
    echo "reason: 性能分析文档已生成，等待决定是否实施"
    ;;
  *)
    echo "[错误] 不支持的 mode: $MODE" >&2
    exit 1
    ;;
esac
