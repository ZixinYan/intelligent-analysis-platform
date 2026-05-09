<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { WorkflowNodeData } from '@/types/workflow'
import { resolveNodeIcon } from '@/utils/node-preview'

const props = defineProps<{
  data: WorkflowNodeData
}>()

const categoryColor = computed(() => ({
  QUERY:      '#3b82f6',
  COMPUTE:    '#8b5cf6',
  OUTPUT:     '#10b981',
  GOVERNANCE: '#f59e0b',
}[props.data.meta?.category ?? 'QUERY'] ?? '#64748b'))

const categoryLabel = computed(() => ({
  QUERY:      '取数',
  COMPUTE:    '计算',
  OUTPUT:     '输出',
  GOVERNANCE: '治理',
}[props.data.meta?.category ?? 'QUERY'] ?? ''))

const statusConfig = computed(() => ({
  idle:    { color: '#64748b', label: '就绪',   dot: false },
  draft:   { color: '#f59e0b', label: '草稿',   dot: false },
  valid:   { color: '#38bdf8', label: '已就绪', dot: false },
  running: { color: '#a855f7', label: '运行中', dot: true  },
  success: { color: '#22c55e', label: '成功',   dot: false },
  error:   { color: '#ef4444', label: '错误',   dot: false },
}[props.data.status] ?? { color: '#64748b', label: props.data.status, dot: false }))

interface UsageHint { label: string; icon: string }

const usageHints = computed<UsageHint[]>(() => {
  const nodeType = props.data.meta?.nodeType ?? ''
  const hintMap: Record<string, UsageHint[]> = {
    sql_query: [
      { label: 'SQL 取数', icon: '📝' },
      { label: '变量参数化', icon: '⚡' },
      { label: '多数据源', icon: '🔗' },
    ],
    aggregate: [
      { label: '分组聚合', icon: '∑' },
      { label: 'SUM / AVG / COUNT', icon: '📊' },
      { label: '输出汇总', icon: '📤' },
    ],
    time_series_compute: [
      { label: '同比 / 环比', icon: '📈' },
      { label: '滚动均值', icon: '〰' },
      { label: '天 / 周 / 月粒度', icon: '📅' },
    ],
    pivot: [
      { label: '行列转换', icon: '⊞' },
      { label: '矩阵交叉分析', icon: '🔢' },
      { label: '维度展开为列', icon: '🔄' },
    ],
    filter: [
      { label: '条件筛选', icon: '🔽' },
      { label: '多条件组合', icon: '⊕' },
      { label: '空值处理', icon: '∅' },
    ],
    sort: [
      { label: '多字段排序', icon: '↕' },
      { label: '升序 / 降序', icon: '🔀' },
      { label: '排序优先级', icon: '🎯' },
    ],
    formula: [
      { label: '表达式计算', icon: 'ƒ' },
      { label: '新增计算列', icon: '➕' },
      { label: '数学 / 字符串', icon: '🔣' },
    ],
    python_script: [
      { label: 'Python 3', icon: '🐍' },
      { label: '自定义变换', icon: '⚙️' },
      { label: 'rows: List[dict]', icon: '📋' },
    ],
    java_code: [
      { label: 'Java 执行', icon: '☕' },
      { label: '高性能处理', icon: '⚡' },
      { label: 'transform(rows)', icon: '🔧' },
    ],
    chart_output: [
      { label: '图表渲染', icon: '📊' },
      { label: '折线 / 柱状 / 饼图', icon: '📈' },
      { label: 'X/Y 轴映射', icon: '🔗' },
    ],
    table_output: [
      { label: '分页表格', icon: '📋' },
      { label: '自定义显示列', icon: '⚙️' },
      { label: '数据下载', icon: '⬇️' },
    ],
  }
  return hintMap[nodeType] ?? []
})

const inputPorts  = computed(() => props.data.meta?.inputPorts  ?? [])
const outputPorts = computed(() => props.data.meta?.outputPorts ?? [])

const visibleTags = computed(() => (props.data.meta?.tags ?? []).slice(0, 3))
</script>

<template>
  <div class="ans" :style="{ '--cat': categoryColor }">
    <!-- 顶部渐变高亮线 -->
    <div class="ans__top-line" />
    <!-- 左侧分类色条 -->
    <div class="ans__accent" />

    <Handle id="input"  type="target" :position="Position.Left"  class="ans__handle ans__handle--in" />
    <Handle id="output" type="source" :position="Position.Right" class="ans__handle ans__handle--out" />

    <!-- 头部：图标 + 名称 + 状态 -->
    <div class="ans__header">
      <div class="ans__icon">{{ resolveNodeIcon(data.meta) }}</div>
      <div class="ans__title-group">
        <div class="ans__title">{{ data.title }}</div>
        <div class="ans__meta-row">
          <span class="ans__category-badge">{{ categoryLabel }}</span>
          <span class="ans__subtype">{{ data.meta?.displayName ?? data.nodeType }}</span>
        </div>
      </div>
      <div class="ans__status" :class="{ 'ans__status--pulse': statusConfig.dot }" :style="{ '--sc': statusConfig.color }">
        <span v-if="statusConfig.dot" class="ans__status-dot" />
        {{ statusConfig.label }}
      </div>
    </div>

    <!-- 分割线 -->
    <div class="ans__divider" />

    <!-- 用法说明 -->
    <div v-if="usageHints.length" class="ans__usage">
      <div class="ans__section-label">用法</div>
      <div class="ans__hints">
        <span v-for="hint in usageHints" :key="hint.label" class="ans__hint">
          <span class="ans__hint-icon">{{ hint.icon }}</span>{{ hint.label }}
        </span>
      </div>
    </div>

    <!-- 当前配置预览 -->
    <div v-if="data.preview?.length" class="ans__preview">
      <div class="ans__section-label">当前配置</div>
      <div class="ans__preview-body">
        <div v-for="line in data.preview" :key="line" class="ans__preview-line">
          <span class="ans__preview-bullet">›</span>{{ line }}
        </div>
      </div>
    </div>

    <!-- 数据流：输入端口 → 输出端口 -->
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

    <!-- 底部：标签 -->
    <div v-if="visibleTags.length" class="ans__footer">
      <div class="ans__tags">
        <span v-for="tag in visibleTags" :key="tag" class="ans__tag">{{ tag }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ans {
  position: relative;
  min-width: 280px;
  max-width: 340px;
  background: linear-gradient(160deg, #131c2e 0%, #0d1420 100%);
  border: 1px solid rgba(255,255,255,0.07);
  border-left: none;
  border-radius: 14px;
  box-shadow:
    0 0 0 1px rgba(0,0,0,0.4),
    0 8px 32px rgba(0,0,0,0.5),
    0 0 24px -8px var(--cat);
  overflow: hidden;
  font-family: inherit;
}

/* ── 顶部高亮线 ───────────────────────────── */
.ans__top-line {
  position: absolute;
  top: 0; left: 4px; right: 0;
  height: 1.5px;
  background: linear-gradient(90deg, color-mix(in srgb, var(--cat) 70%, transparent), transparent 80%);
  border-radius: 0 14px 0 0;
}

/* ── 左侧色条 ─────────────────────────────── */
.ans__accent {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, var(--cat), color-mix(in srgb, var(--cat) 40%, #0d1420));
  border-radius: 14px 0 0 14px;
}

/* ── Vue-Flow handles ─────────────────────── */
.ans__handle {
  width: 10px !important;
  height: 10px !important;
  border: 2px solid var(--cat) !important;
  background: #0d1420 !important;
  border-radius: 50% !important;
  transition: box-shadow 0.15s !important;
}
.ans__handle:hover {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--cat) 30%, transparent) !important;
}
.ans__handle--in  { left: -6px !important; }
.ans__handle--out { right: -6px !important; }

/* ── 头部 ─────────────────────────────────── */
.ans__header {
  display: grid;
  grid-template-columns: 44px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 14px 14px 12px 18px;
}

.ans__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--cat) 15%, #1e293b);
  border: 1px solid color-mix(in srgb, var(--cat) 30%, transparent);
  font-size: 20px;
  line-height: 1;
  flex-shrink: 0;
}

.ans__title-group { min-width: 0; }

.ans__title {
  font-size: 13px;
  font-weight: 700;
  color: #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: 0.01em;
}

.ans__meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.ans__category-badge {
  font-size: 10px;
  font-weight: 700;
  color: var(--cat);
  background: color-mix(in srgb, var(--cat) 15%, transparent);
  border: 1px solid color-mix(in srgb, var(--cat) 30%, transparent);
  border-radius: 4px;
  padding: 1px 6px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
  flex-shrink: 0;
}

.ans__subtype {
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}

/* ── 状态徽章 ─────────────────────────────── */
.ans__status {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--sc);
  background: color-mix(in srgb, var(--sc) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--sc) 28%, transparent);
  flex-shrink: 0;
}

.ans__status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sc);
  animation: pulse 1.4s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1;   transform: scale(1); }
  50%       { opacity: 0.4; transform: scale(0.7); }
}

/* ── 分割线 ───────────────────────────────── */
.ans__divider {
  height: 1px;
  margin: 0 14px 0 18px;
  background: linear-gradient(90deg, color-mix(in srgb, var(--cat) 30%, transparent), transparent 70%);
}

/* ── 公共 section label ───────────────────── */
.ans__section-label {
  font-size: 10px;
  font-weight: 700;
  color: #475569;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 6px;
}

/* ── 用法 ─────────────────────────────────── */
.ans__usage {
  padding: 10px 14px 0 18px;
}

.ans__hints {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.ans__hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #94a3b8;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
  border-radius: 6px;
  padding: 3px 8px;
  white-space: nowrap;
  transition: border-color 0.12s, color 0.12s;
}

.ans__hint:hover {
  border-color: color-mix(in srgb, var(--cat) 40%, transparent);
  color: #cbd5e1;
}

.ans__hint-icon {
  font-size: 12px;
  flex-shrink: 0;
}

/* ── 配置预览 ─────────────────────────────── */
.ans__preview {
  margin: 10px 14px 0 18px;
}

.ans__preview-body {
  padding: 7px 10px;
  border-radius: 8px;
  background: rgba(15,23,42,0.8);
  border: 1px solid #1e293b;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.ans__preview-line {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  display: flex;
  align-items: center;
  gap: 5px;
}

.ans__preview-bullet {
  color: color-mix(in srgb, var(--cat) 70%, transparent);
  font-weight: 700;
  flex-shrink: 0;
}

/* ── 数据流指示器 ─────────────────────────── */
.ans__flow {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 14px 0 18px;
  padding: 7px 10px;
  border-radius: 8px;
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.05);
  overflow: hidden;
}

.ans__flow-port {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  flex: 1;
}

.ans__flow-badge {
  font-size: 9px;
  font-weight: 800;
  padding: 1px 5px;
  border-radius: 3px;
  letter-spacing: 0.06em;
  flex-shrink: 0;
}

.ans__flow-badge--in {
  background: rgba(56,189,248,0.12);
  color: #38bdf8;
  border: 1px solid rgba(56,189,248,0.25);
}

.ans__flow-badge--out {
  background: rgba(167,139,250,0.12);
  color: #a78bfa;
  border: 1px solid rgba(167,139,250,0.25);
}

.ans__flow-name {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ans__flow-type {
  font-size: 10px;
  color: #475569;
  font-family: ui-monospace, monospace;
  white-space: nowrap;
  flex-shrink: 0;
}

.ans__flow-arrow {
  font-size: 12px;
  color: #334155;
  flex-shrink: 0;
}

/* ── 底部标签 ─────────────────────────────── */
.ans__footer {
  display: flex;
  align-items: center;
  padding: 10px 14px 12px 18px;
}

.ans__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.ans__tag {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
  color: #475569;
}
</style>
