#!/usr/bin/env bash
set -euo pipefail

PHASE="clarify"
REQUIREMENT="${1:-}"
PROJECT_PATH=""
ANSWERS_FILE=""
DOC_FILE=""
REVIEW_FILE=""

if [[ -z "$REQUIREMENT" ]]; then
  echo "用法: $0 \"需求描述\" [--phase <phase>] [--project <path>] [--answers <file>] [--doc-file <file>] [--review-file <file>]" >&2
  exit 1
fi

shift || true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --phase) PHASE="$2"; shift 2 ;;
    --project) PROJECT_PATH="$2"; shift 2 ;;
    --answers) ANSWERS_FILE="$2"; shift 2 ;;
    --doc-file) DOC_FILE="$2"; shift 2 ;;
    --review-file) REVIEW_FILE="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
run_phase() {
  local script_name="$1"
  shift
  bash "$SCRIPT_DIR/$script_name" "$@"
}

case "$PHASE" in
  clarify)
    args=(--requirement "$REQUIREMENT")
    [[ -n "$PROJECT_PATH" ]] && args+=(--project "$PROJECT_PATH")
    run_phase generic-dev-impl-clarify.sh "${args[@]}"
    ;;
  spec)
    args=(--mode spec --requirement "$REQUIREMENT")
    [[ -n "$PROJECT_PATH" ]] && args+=(--project "$PROJECT_PATH")
    [[ -n "$ANSWERS_FILE" ]] && args+=(--answers "$ANSWERS_FILE")
    run_phase generic-dev-impl-docgen.sh "${args[@]}"
    ;;
  implplan)
    args=(--mode implplan --requirement "$REQUIREMENT")
    [[ -n "$PROJECT_PATH" ]] && args+=(--project "$PROJECT_PATH")
    [[ -n "$ANSWERS_FILE" ]] && args+=(--answers "$ANSWERS_FILE")
    [[ -n "$DOC_FILE" ]] && args+=(--doc-file "$DOC_FILE")
    run_phase generic-dev-impl-docgen.sh "${args[@]}"
    ;;
  perf)
    args=(--mode perf --requirement "$REQUIREMENT")
    [[ -n "$PROJECT_PATH" ]] && args+=(--project "$PROJECT_PATH")
    [[ -n "$ANSWERS_FILE" ]] && args+=(--answers "$ANSWERS_FILE")
    [[ -n "$DOC_FILE" ]] && args+=(--doc-file "$DOC_FILE")
    run_phase generic-dev-impl-docgen.sh "${args[@]}"
    ;;
  ultraplan)
    args=(--requirement "$REQUIREMENT")
    [[ -n "$PROJECT_PATH" ]] && args+=(--project "$PROJECT_PATH")
    [[ -n "$ANSWERS_FILE" ]] && args+=(--answers "$ANSWERS_FILE")
    [[ -n "$DOC_FILE" ]] && args+=(--doc-file "$DOC_FILE")
    run_phase generic-dev-impl-ultraplan.sh "${args[@]}"
    ;;
  implement)
    args=(--requirement "$REQUIREMENT")
    [[ -n "$PROJECT_PATH" ]] && args+=(--project "$PROJECT_PATH")
    [[ -n "$DOC_FILE" ]] && args+=(--doc-file "$DOC_FILE")
    run_phase generic-dev-impl-implement-hint.sh "${args[@]}"
    ;;
  ultrareview)
    args=(--requirement "$REQUIREMENT")
    [[ -n "$PROJECT_PATH" ]] && args+=(--project "$PROJECT_PATH")
    [[ -n "$DOC_FILE" ]] && args+=(--doc-file "$DOC_FILE")
    [[ -n "$REVIEW_FILE" ]] && args+=(--review-file "$REVIEW_FILE")
    run_phase generic-dev-impl-ultrareview.sh "${args[@]}"
    ;;
  *)
    echo "不支持的 phase: $PHASE" >&2
    exit 1
    ;;
esac
