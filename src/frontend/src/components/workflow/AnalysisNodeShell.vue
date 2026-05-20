<script setup lang="ts">
import { computed, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { WorkflowNodeData } from '@/types/workflow'
import { resolveNodeIcon } from '@/utils/node-preview'
import { normalizeNodeType } from '@/constants/analysis-nodes'
import { useWorkflowStore } from '@/stores/workflow'
import { resolveRendererModel } from '@/components/output/renderer'
import type { WorkflowInsertTrigger } from './insert-types'

const props = defineProps<{
  id: string
  data: WorkflowNodeData
}>()

const emit = defineEmits<{
  'open-insert-picker': [trigger: WorkflowInsertTrigger]
}>()

type InsertTriggerInput =
  | Omit<Extract<WorkflowInsertTrigger, { kind: 'node-output' }>, 'anchor'>
  | Omit<Extract<WorkflowInsertTrigger, { kind: 'node-input' }>, 'anchor'>

const workflow = useWorkflowStore()

function handleRun() {
  workflow.runNodeDebug(props.id)
}

function openInsertPicker(event: MouseEvent, trigger: InsertTriggerInput) {
  emit('open-insert-picker', {
    ...trigger,
    anchor: { x: event.clientX + 12, y: event.clientY + 12 },
  } as WorkflowInsertTrigger)
}

const categoryColor = computed(() => ({
  QUERY: '#3b82f6',
  COMPUTE: '#8b5cf6',
  OUTPUT: '#10b981',
  GOVERNANCE: '#f59e0b',
  ANALYSIS: '#06b6d4',
}[props.data.meta?.category ?? 'QUERY'] ?? '#64748b'))

const categoryLabel = computed(() => ({
  QUERY: '取数',
  COMPUTE: '计算',
  OUTPUT: '输出',
  GOVERNANCE: '治理',
  ANALYSIS: '分析',
}[props.data.meta?.category ?? 'QUERY'] ?? ''))

const businessType = computed(() => props.data.type || props.data.nodeType || props.data.meta?.nodeType || '')
const isConditionNode = computed(() => normalizeNodeType(businessType.value) === 'condition')

const statusConfig = computed(() => ({
  idle: { color: '#64748b', label: '就绪', dot: false },
  draft: { color: '#f59e0b', label: '草稿', dot: false },
  valid: { color: '#38bdf8', label: '已就绪', dot: false },
  running: { color: '#a855f7', label: '运行中', dot: true },
  success: { color: '#22c55e', label: '成功', dot: false },
  error: { color: '#ef4444', label: '错误', dot: false },
  skipped: { color: '#94a3b8', label: '已跳过', dot: false },
}[props.data.status] ?? { color: '#64748b', label: props.data.status, dot: false }))

interface UsageHint { label: string; icon: string }

const usageHints = computed<UsageHint[]>(() => {
  const nodeType = normalizeNodeType(businessType.value)
  const hintMap: Record<string, UsageHint[]> = {
    sql_query: [
      { label: '连接数据源，执行 SQL 查询', icon: '📝' },
      { label: '支持 {{变量}} 参数化插值', icon: '⚡' },
      { label: '可切换多个数据源', icon: '🔗' },
    ],
    aggregate: [
      { label: '按维度分组聚合数据', icon: '∑' },
      { label: '支持 SUM / AVG / COUNT / MAX / MIN', icon: '📊' },
      { label: '结果作为下游汇总输入', icon: '📤' },
    ],
    time_series_compute: [
      { label: '计算同比、环比增长率', icon: '📈' },
      { label: '滚动均值、累计求和', icon: '〰' },
      { label: '按天 / 周 / 月粒度对齐', icon: '📅' },
    ],
    pivot: [
      { label: '行列转换，生成交叉表', icon: '⊞' },
      { label: '矩阵形式展示多维数据', icon: '🔢' },
      { label: '将维度值展开为独立列', icon: '🔄' },
    ],
    filter: [
      { label: '按条件筛选数据行', icon: '🔽' },
      { label: '支持 AND / OR 多条件组合', icon: '⊕' },
      { label: '处理空值、异常值', icon: '∅' },
    ],
    sort: [
      { label: '对结果集多字段排序', icon: '↕' },
      { label: '支持升序 / 降序混合', icon: '🔀' },
      { label: '设定排序优先级', icon: '🎯' },
    ],
    formula: [
      { label: '写表达式生成新计算列', icon: 'ƒ' },
      { label: '引用已有列参与运算', icon: '➕' },
      { label: '支持数学、字符串、日期函数', icon: '🔣' },
    ],
    python_script: [
      { label: '用 Python 3 自定义数据变换', icon: '🐍' },
      { label: '入参 rows: List[dict]，返回同格式', icon: '📋' },
      { label: '可引入标准库进行复杂处理', icon: '⚙️' },
    ],
    java_code: [
      { label: '用 Java 编写高性能处理逻辑', icon: '☕' },
      { label: '实现 transform(List<Map>) 方法', icon: '🔧' },
      { label: '适合计算密集型场景', icon: '⚡' },
    ],
    chart_output: [
      { label: '将数据渲染为可视化图表', icon: '📊' },
      { label: '支持折线、柱状、饼图等', icon: '📈' },
      { label: '配置 X / Y 轴字段映射', icon: '🔗' },
    ],
    table_output: [
      { label: '以分页表格展示数据', icon: '📋' },
      { label: '自定义显示列与列宽', icon: '⚙️' },
      { label: '支持数据导出下载', icon: '⬇️' },
    ],
    condition: [
      { label: '根据规则输出 true / false 分支', icon: '⎇' },
      { label: '下游按条件边继续编排', icon: '➡️' },
    ],
  }
  return hintMap[nodeType] ?? []
})

const inputPorts = computed(() => props.data.meta?.inputPorts ?? [])
const outputPorts = computed(() => props.data.meta?.outputPorts ?? [])
const visibleTags = computed(() => (props.data.meta?.tags ?? []).slice(0, 3))
const showTooltip = ref(false)
const isOutputNode = computed(() => props.data.meta?.category === 'OUTPUT')
const resultSummary = computed(() => {
  if (!isOutputNode.value || props.data.status !== 'success') return null
  const debugResult = props.data.debugResult
  if (!debugResult?.result) return null
  const model = resolveRendererModel(debugResult.result, 'runtime')
  if (model.kind === 'chart') return { icon: '📊', text: `${model.chartType} 图表已渲染` }
  if (model.kind === 'table') return { icon: '📋', text: `${model.rows.length.toLocaleString()} 行数据` }
  return null
})
</script>

<template>
  <div class="ans-outer">
    <Transition name="tooltip-fade">
      <div v-if="showTooltip && usageHints.length" class="ans__tooltip" :style="{ '--cat': categoryColor }">
        <div class="ans__tooltip-header">
          <span class="ans__tooltip-icon">{{ resolveNodeIcon(data.meta, businessType) }}</span>
          <div>
            <div class="ans__tooltip-name">{{ data.meta?.displayName ?? businessType }}</div>
            <div class="ans__tooltip-category">{{ categoryLabel }}节点</div>
          </div>
        </div>
        <div v-if="data.meta?.description" class="ans__tooltip-desc">
          {{ data.meta.description }}
        </div>
        <div class="ans__tooltip-divider" />
        <div class="ans__tooltip-hints">
          <div v-for="hint in usageHints" :key="hint.label" class="ans__tooltip-hint">
            <span class="ans__tooltip-hint-icon">{{ hint.icon }}</span>
            <span class="ans__tooltip-hint-label">{{ hint.label }}</span>
          </div>
        </div>
      </div>
    </Transition>

    <Handle id="input" type="target" :position="Position.Left" class="ans__handle ans__handle--in" />
    <button
      class="ans__insert-button ans__insert-button--in"
      @click.stop="openInsertPicker($event, { kind: 'node-input', nodeId: id, targetHandle: 'input' })"
    >
      +
    </button>
    <template v-if="isConditionNode">
      <Handle id="true" type="source" :position="Position.Right" class="ans__handle ans__handle--out ans__handle--true" style="top: calc(35% - 10px)" />
      <Handle id="false" type="source" :position="Position.Right" class="ans__handle ans__handle--out ans__handle--false" style="top: calc(65% - 10px)" />
      <button
        class="ans__insert-button ans__insert-button--out ans__insert-button--true"
        style="top: calc(35% - 10px)"
        @click.stop="openInsertPicker($event, { kind: 'node-output', nodeId: id, sourceHandle: 'true' })"
      >
        +
      </button>
      <button
        class="ans__insert-button ans__insert-button--out ans__insert-button--false"
        style="top: calc(65% - 10px)"
        @click.stop="openInsertPicker($event, { kind: 'node-output', nodeId: id, sourceHandle: 'false' })"
      >
        +
      </button>
    </template>
    <template v-else>
      <Handle id="output" type="source" :position="Position.Right" class="ans__handle ans__handle--out" />
      <button
        class="ans__insert-button ans__insert-button--out"
        @click.stop="openInsertPicker($event, { kind: 'node-output', nodeId: id, sourceHandle: 'output' })"
      >
        +
      </button>
    </template>

    <div class="ans" :style="{ '--cat': categoryColor }">
      <div class="ans__top-line" />
      <div class="ans__accent" />
      <div class="ans__header">
        <div class="ans__icon">{{ resolveNodeIcon(data.meta, businessType) }}</div>
        <div class="ans__title-group">
          <div class="ans__title">{{ data.title }}</div>
          <div class="ans__meta-row">
            <span class="ans__category-badge">{{ categoryLabel }}</span>
            <span class="ans__subtype">{{ data.meta?.displayName ?? businessType }}</span>
          </div>
        </div>
        <div class="ans__header-right">
          <div class="ans__status" :class="{ 'ans__status--pulse': statusConfig.dot }" :style="{ '--sc': statusConfig.color }">
            <span v-if="statusConfig.dot" class="ans__status-dot" />
            {{ statusConfig.label }}
          </div>
          <div class="ans__header-actions">
            <button class="ans__run-btn" title="运行此节点" @click.stop="handleRun">▷</button>
            <button v-if="usageHints.length" class="ans__info-btn" @mouseenter="showTooltip = true" @mouseleave="showTooltip = false">?</button>
          </div>
        </div>
      </div>
      <div class="ans__divider" />
      <div v-if="data.preview?.length" class="ans__preview">
        <div class="ans__section-label">当前配置</div>
        <div class="ans__preview-body">
          <div v-for="line in data.preview" :key="line" class="ans__preview-line">
            <span class="ans__preview-bullet">›</span>{{ line }}
          </div>
        </div>
      </div>
      <div v-if="inputPorts.length || outputPorts.length" class="ans__flow">
        <div v-if="inputPorts[0]" class="ans__flow-port ans__flow-port--in">
          <span class="ans__flow-badge ans__flow-badge--in">IN</span>
          <span class="ans__flow-name">{{ inputPorts[0].label }}</span>
          <span class="ans__flow-type">{{ inputPorts[0].valueType }}</span>
        </div>
        <span v-if="inputPorts[0] && outputPorts[0]" class="ans__flow-arrow">→</span>
        <div v-if="outputPorts[0]" class="ans__flow-port ans__flow-port--out">
          <span class="ans__flow-badge ans__flow-badge--out">OUT</span>
          <span class="ans__flow-name">{{ outputPorts[0].label }}</span>
          <span class="ans__flow-type">{{ outputPorts[0].valueType }}</span>
        </div>
      </div>
      <div v-if="visibleTags.length" class="ans__footer">
        <div class="ans__tags">
          <span v-for="tag in visibleTags" :key="tag" class="ans__tag">{{ tag }}</span>
        </div>
      </div>
      <div v-if="resultSummary" class="ans__result-badge">
        <span class="ans__result-badge-icon">{{ resultSummary.icon }}</span>
        <span class="ans__result-badge-text">{{ resultSummary.text }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ans-outer { position: relative; display: inline-block; }
.ans__tooltip { position: absolute; left: calc(100% + 14px); top: 0; width: 240px; background: var(--iap-panel-bg); border: 1px solid color-mix(in srgb, var(--cat) 24%, var(--iap-divider)); border-radius: var(--iap-radius-lg); padding: 14px 15px; box-shadow: var(--iap-shadow-panel), 0 0 28px -14px var(--cat); z-index: 9999; pointer-events: none; }
.ans__tooltip-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.ans__tooltip-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 9px; background: color-mix(in srgb, var(--cat) 15%, var(--iap-surface-secondary)); border: 1px solid color-mix(in srgb, var(--cat) 28%, var(--iap-divider)); font-size: 17px; flex-shrink: 0; }
.ans__tooltip-name { font-size: 13px; font-weight: 700; color: var(--iap-text-primary); letter-spacing: 0.01em; }
.ans__tooltip-category { font-size: 11px; color: var(--cat); margin-top: 2px; opacity: 0.8; }
.ans__tooltip-desc { font-size: 12px; color: var(--iap-text-tertiary); line-height: 1.6; margin-bottom: 10px; }
.ans__tooltip-divider { height: 1px; background: linear-gradient(90deg, color-mix(in srgb, var(--cat) 30%, var(--iap-divider)), transparent 70%); margin-bottom: 10px; }
.ans__tooltip-hints { display: flex; flex-direction: column; gap: 6px; }
.ans__tooltip-hint { display: flex; align-items: flex-start; gap: 8px; font-size: 12px; color: var(--iap-text-secondary); line-height: 1.4; }
.ans__tooltip-hint-icon { font-size: 13px; flex-shrink: 0; width: 18px; text-align: center; margin-top: 1px; }
.ans__tooltip-hint-label { flex: 1; }
.tooltip-fade-enter-active, .tooltip-fade-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.tooltip-fade-enter-from, .tooltip-fade-leave-to { opacity: 0; transform: translateX(-4px); }
.ans { position: relative; min-width: 280px; max-width: 340px; background: linear-gradient(180deg, color-mix(in srgb, var(--cat) 3%, var(--iap-node-bg)), var(--iap-node-bg)); border: 1px solid var(--iap-node-border); border-left: none; border-radius: var(--iap-radius-node); box-shadow: var(--iap-shadow-node), 0 0 22px -14px var(--cat); overflow: hidden; font-family: inherit; }
.ans__top-line { position: absolute; top: 0; left: 4px; right: 0; height: 1.5px; background: linear-gradient(90deg, color-mix(in srgb, var(--cat) 70%, transparent), transparent 80%); border-radius: 0 var(--iap-radius-node) 0 0; }
.ans__accent { position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: linear-gradient(180deg, var(--cat), color-mix(in srgb, var(--cat) 40%, var(--iap-node-bg))); border-radius: var(--iap-radius-node) 0 0 var(--iap-radius-node); }
.ans__handle { width: 20px !important; height: 20px !important; border: 2.5px solid var(--cat) !important; background: var(--iap-panel-bg) !important; border-radius: 50% !important; display: flex !important; align-items: center !important; justify-content: center !important; cursor: crosshair !important; transition: all 0.2s ease !important; box-shadow: 0 0 0 3px var(--iap-shadow-heavy), 0 0 12px -2px var(--cat) !important; z-index: 10 !important; }
.ans__handle::after { content: '+' !important; font-size: 14px !important; font-weight: 700 !important; color: var(--cat) !important; line-height: 1 !important; transition: inherit !important; }
.ans__handle:hover { width: 26px !important; height: 26px !important; border-width: 3px !important; background: var(--iap-panel-bg) !important; box-shadow: 0 0 0 4px var(--iap-shadow-heavy), 0 0 0 8px color-mix(in srgb, var(--cat) 25%, transparent), 0 0 20px -2px var(--cat) !important; transform: translate(-3px, -3px) !important; }
.ans__handle:hover::after { font-size: 18px !important; }
.ans__handle--in { left: -12px !important; top: calc(50% - 10px) !important; }
.ans__handle--out { right: -12px !important; top: calc(50% - 10px) !important; }
.ans__handle--in:hover { left: -15px !important; top: calc(50% - 13px) !important; }
.ans__handle--out:hover { right: -15px !important; top: calc(50% - 13px) !important; }
.ans__handle--true { border-color: #22c55e !important; box-shadow: 0 0 0 3px var(--iap-shadow-heavy), 0 0 12px -2px #22c55e !important; }
.ans__handle--true::after { color: #22c55e !important; }
.ans__handle--false { border-color: #ef4444 !important; box-shadow: 0 0 0 3px var(--iap-shadow-heavy), 0 0 12px -2px #ef4444 !important; }
.ans__handle--false::after { color: #ef4444 !important; }
.ans__insert-button { position: absolute; z-index: 11; width: 24px; height: 24px; display: grid; place-items: center; border: 1px solid var(--cat); border-radius: 999px; background: var(--iap-panel-bg); color: var(--cat); box-shadow: var(--iap-shadow-panel); cursor: pointer; }
.ans__insert-button--in { left: -36px; top: calc(50% - 12px); }
.ans__insert-button--out { right: -36px; top: calc(50% - 12px); }
.ans__insert-button--true { color: #22c55e; border-color: #22c55e; }
.ans__insert-button--false { color: #ef4444; border-color: #ef4444; }
.ans__header { display: grid; grid-template-columns: 44px 1fr auto; align-items: center; gap: 10px; padding: 14px 12px 12px 18px; }
.ans__icon { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 12px; background: color-mix(in srgb, var(--cat) 14%, var(--iap-surface-secondary)); border: 1px solid color-mix(in srgb, var(--cat) 28%, var(--iap-divider)); font-size: 20px; line-height: 1; flex-shrink: 0; }
.ans__title-group { min-width: 0; }
.ans__title { font-size: 13px; font-weight: 700; color: var(--iap-text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; letter-spacing: 0.01em; }
.ans__meta-row { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.ans__category-badge { font-size: 10px; font-weight: 700; color: var(--cat); background: color-mix(in srgb, var(--cat) 15%, transparent); border: 1px solid color-mix(in srgb, var(--cat) 30%, transparent); border-radius: 4px; padding: 1px 6px; letter-spacing: 0.04em; text-transform: uppercase; white-space: nowrap; flex-shrink: 0; }
.ans__subtype { font-size: 11px; color: var(--iap-text-tertiary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-family: 'JetBrains Mono', ui-monospace, monospace; }
.ans__header-right { display: flex; flex-direction: column; align-items: flex-end; gap: 5px; flex-shrink: 0; }
.ans__header-actions { display: flex; align-items: center; gap: 4px; }
.ans__run-btn { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 6px; border: 1px solid color-mix(in srgb, var(--cat) 35%, transparent); background: color-mix(in srgb, var(--cat) 10%, transparent); color: var(--cat); font-size: 11px; cursor: pointer; transition: all 0.15s; padding: 0; line-height: 1; font-family: inherit; }
.ans__run-btn:hover { background: var(--cat); color: #fff; border-color: transparent; box-shadow: 0 0 8px -2px var(--cat); }
.ans__status { display: flex; align-items: center; gap: 5px; padding: 3px 9px; border-radius: 999px; font-size: 11px; font-weight: 600; white-space: nowrap; color: var(--sc); background: color-mix(in srgb, var(--sc) 12%, transparent); border: 1px solid color-mix(in srgb, var(--sc) 28%, transparent); }
.ans__status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: var(--sc); animation: pulse 1.4s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.4; transform: scale(0.7); } }
.ans__info-btn { display: grid; place-items: center; width: 20px; height: 20px; border-radius: 50%; border: 1px solid var(--iap-divider); background: color-mix(in srgb, var(--iap-panel-bg) 92%, transparent); color: var(--iap-text-placeholder); font-size: 11px; font-weight: 700; cursor: default; transition: border-color 0.12s, color 0.12s, background 0.12s; line-height: 1; padding: 0; font-family: inherit; }
.ans__info-btn:hover { border-color: color-mix(in srgb, var(--cat) 50%, transparent); color: var(--cat); background: color-mix(in srgb, var(--cat) 10%, transparent); }
.ans__divider { height: 1px; margin: 0 14px 0 18px; background: linear-gradient(90deg, color-mix(in srgb, var(--cat) 26%, var(--iap-divider)), transparent 70%); }
.ans__section-label { font-size: 10px; font-weight: 700; color: var(--iap-text-tertiary); letter-spacing: 0.08em; text-transform: uppercase; margin-bottom: 6px; }
.ans__preview { padding: 12px 14px 0 18px; }
.ans__preview-body { display: grid; gap: 6px; border: 1px solid var(--iap-divider); background: var(--iap-code-bg); border-radius: 10px; padding: 10px 12px; }
.ans__preview-line { display: flex; gap: 6px; font-size: 12px; line-height: 1.5; color: var(--iap-text-secondary); }
.ans__preview-bullet { color: var(--cat); }
.ans__flow { display: flex; align-items: center; gap: 8px; padding: 14px 14px 0 18px; }
.ans__flow-port { flex: 1; min-width: 0; border: 1px solid var(--iap-divider); border-radius: 10px; background: color-mix(in srgb, var(--cat) 6%, var(--iap-surface-secondary)); padding: 8px 10px; }
.ans__flow-arrow { color: var(--iap-text-tertiary); font-size: 13px; }
.ans__flow-badge { display: inline-flex; align-items: center; padding: 1px 6px; border-radius: 999px; font-size: 10px; font-weight: 700; margin-bottom: 6px; }
.ans__flow-badge--in { color: var(--cat); background: color-mix(in srgb, var(--cat) 12%, transparent); }
.ans__flow-badge--out { color: var(--cat); background: color-mix(in srgb, var(--cat) 16%, transparent); }
.ans__flow-name { display: block; font-size: 12px; font-weight: 600; color: var(--iap-text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ans__flow-type { display: block; margin-top: 4px; font-size: 11px; color: var(--iap-text-tertiary); }
.ans__footer { padding: 12px 14px 14px 18px; }
.ans__tags { display: flex; flex-wrap: wrap; gap: 6px; }
.ans__tag { border-radius: 999px; border: 1px solid var(--iap-divider); background: var(--iap-surface-secondary); color: var(--iap-text-secondary); font-size: 11px; padding: 3px 8px; }
.ans__result-badge { display: flex; align-items: center; gap: 6px; margin: 0 14px 14px 18px; border-radius: 10px; border: 1px solid var(--iap-success-border); background: var(--iap-success-bg); color: var(--iap-success-text); padding: 8px 10px; font-size: 12px; font-weight: 600; }
</style>
