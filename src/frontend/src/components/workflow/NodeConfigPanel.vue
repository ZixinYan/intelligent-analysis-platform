<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import FormRenderer from '@/components/form/FormRenderer.vue'
import NodeRunPanel from '@/components/workflow/NodeRunPanel.vue'
import QueryActionsBar from '@/components/query/QueryActionsBar.vue'
import AiRecommendBadge from '@/components/ai/AiRecommendBadge.vue'
import type { WorkflowNode } from '@/types/workflow'
import { usePanelController } from '@/composables/usePanelController'
import { useWorkflowStore, useWorkflowDebugStore, useWorkflowGraphStore } from '@/stores/workflow'
import { recommendChart } from '@/api/ai'
import type { ChartRecommendationDTO } from '@/types/contract'
import { getBusinessNodeType } from '@/adapters/workflow-graph'

const props = defineProps<{
  node?: WorkflowNode
}>()

const workflow = useWorkflowStore()
const graphStore = useWorkflowGraphStore()
const debugStore = useWorkflowDebugStore()
const { debugActiveTab, debugLoadingNodeId } = storeToRefs(debugStore)
const activeNode = computed(() => props.node)
const nodeData = computed(() => activeNode.value?.data)
const nodeType = computed(() => activeNode.value ? getBusinessNodeType(activeNode.value) : '')
const { draft, meta, schema, schemaLoading, schemaError, candidateSlots, handleUpdate, handleValid } = usePanelController(activeNode)

// ── 节点命名 ──────────────────────────────────────────────────────
const titleEditing = ref(false)
const titleDraft = ref('')

watch(() => props.node?.id, () => { titleEditing.value = false })

function startEditTitle() {
  titleDraft.value = activeNode.value?.data.title ?? ''
  titleEditing.value = true
}

function commitTitle() {
  titleEditing.value = false
  const node = activeNode.value
  const next = titleDraft.value.trim()
  if (!node || !next || next === node.data.title) return
  graphStore.updateNode(node.id, n => ({ ...n, data: { ...n.data, title: next } }))
}

function cancelEditTitle() {
  titleEditing.value = false
}

function onTitleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') commitTitle()
  if (e.key === 'Escape') cancelEditTitle()
}

const categoryLabel: Record<string, string> = {
  QUERY: '取数', COMPUTE: '计算', OUTPUT: '输出', GOVERNANCE: '治理', ANALYSIS: '分析',
}
const categoryColor: Record<string, string> = {
  QUERY: '#3b82f6', COMPUTE: '#8b5cf6', OUTPUT: '#10b981', GOVERNANCE: '#f59e0b', ANALYSIS: '#06b6d4',
}

function collectCodeVarNames(value: unknown) {
  if (Array.isArray(value)) {
    return value
      .map((item) => {
        if (typeof item === 'string') return item
        if (item && typeof item === 'object') {
          const record = item as Record<string, unknown>
          return String(record.name ?? record.key ?? record.field ?? '').trim()
        }
        return ''
      })
      .filter(Boolean)
  }
  if (value && typeof value === 'object') {
    return Object.keys(value as Record<string, unknown>).filter(Boolean)
  }
  if (typeof value === 'string' && value.trim()) {
    return [value.trim()]
  }
  return []
}

function getCodeSummary(config: Record<string, unknown>) {
  const source = [config.code, config.script].find(value => typeof value === 'string' && value.trim()) as string | undefined
  if (!source) return '未填写代码'
  const line = source.split('\n').map(item => item.trim()).find(Boolean)
  return line ? line.slice(0, 72) : '未填写代码'
}

const nodeCategoryLabel = computed(() => categoryLabel[meta.value?.category ?? ''] ?? meta.value?.category ?? '')
const nodeCategoryColor = computed(() => categoryColor[meta.value?.category ?? ''] ?? '#64748b')
const mockInputRaw = ref('{}')
const mockInputError = ref<string>()

const isDebugLoading = computed(() => activeNode.value ? debugLoadingNodeId.value === activeNode.value.id : false)

function handleRunNode() {
  if (activeNode.value) {
    workflow.runNodeDebug(activeNode.value.id)
  }
}

const isSqlQueryNode = computed(() => nodeType.value === 'sql_query')
const isChartOutputNode = computed(() => nodeType.value === 'chart_output')
const isCodeNode = computed(() => nodeType.value === 'python_script' || nodeType.value === 'java_code')
const codeLanguage = computed(() => nodeType.value === 'python_script' ? 'Python' : nodeType.value === 'java_code' ? 'Java' : 'Code')
const codeInputs = computed(() => collectCodeVarNames(draft.inputVars ?? draft.inputs ?? draft.inputVariables))
const codeOutputs = computed(() => collectCodeVarNames(draft.outputs ?? draft.outputVars ?? draft.outputVar ?? draft.resultVar))
const codeSummary = computed(() => getCodeSummary(draft))

function handleSqlUpdate(sql: string) {
  const cleaned = Object.fromEntries(Object.entries(draft).filter(([k]) => k !== '__schema'))
  handleUpdate({ ...cleaned, sqlTemplate: sql })
}

const chartRecommendations = ref<ChartRecommendationDTO[]>([])
const chartRecommendLoading = ref(false)
const chartRecommendError = ref('')

watch(() => activeNode.value?.id, () => {
  chartRecommendations.value = []
  chartRecommendError.value = ''
})

const upstreamFields = computed(() => {
  if (!activeNode.value) return []
  const upstreams = graphStore.getUpstreamNodes(activeNode.value.id)
  // 合并所有上游节点的字段（去重）
  const seen = new Set<string>()
  return upstreams.flatMap(u => u.data.schema?.fields ?? []).filter((f) => {
    const key = f.name ?? f.fieldId ?? ''
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
})

/** 当前节点的所有直接上游节点（可能多个） */
const upstreamNodes = computed(() => {
  if (!activeNode.value) return []
  const graphStore = useWorkflowGraphStore()
  const edges = graphStore.edges.filter(e => e.target === activeNode.value!.id)
  return edges.map(e => graphStore.getNodeById(e.source)).filter(Boolean)
})

/** 为某个上游节点生成 mock 数据骨架（基于 schema 字段） */
function buildMockRowFromSchema(fields: { name: string, valueType?: string }[]) {
  const row: Record<string, unknown> = {}
  for (const f of fields) {
    const t = f.valueType ?? 'STRING'
    if (t === 'LONG' || t === 'DOUBLE') row[f.name] = 0
    else if (t === 'BOOLEAN') row[f.name] = false
    else row[f.name] = ''
  }
  return row
}

/** 根据上游节点 schema 自动生成模板填入 textarea */
function generateMockTemplate() {
  const graphStore = useWorkflowGraphStore()
  if (!activeNode.value || upstreamNodes.value.length === 0) return
  const template: Record<string, unknown> = {}
  for (const up of upstreamNodes.value) {
    if (!up) continue
    const fields = up.data.schema?.fields ?? []
    const row = buildMockRowFromSchema(fields)
    template[up.data.title] = { rows: [row, row] }
  }
  mockInputRaw.value = JSON.stringify(template, null, 2)
  // 同步存储（用 nodeId 作 key）
  const titleToId: Record<string, string> = {}
  graphStore.nodes.forEach(n => { titleToId[n.data.title] = n.id })
  const normalized: Record<string, unknown> = {}
  for (const [key, val] of Object.entries(template)) {
    normalized[titleToId[key] ?? key] = val
  }
  workflow.setNodeMockInputs(activeNode.value.id, normalized)
  mockInputError.value = undefined
}

/** 用上游节点上次 debug 运行的实际结果填充 mock */
function fillFromLastRun() {
  const graphStore = useWorkflowGraphStore()
  if (!activeNode.value || upstreamNodes.value.length === 0) return
  const hasAnyResult = upstreamNodes.value.some(up => up?.data.debugResult?.result)
  if (!hasAnyResult) return
  const template: Record<string, unknown> = {}
  const normalized: Record<string, unknown> = {}
  for (const up of upstreamNodes.value) {
    if (!up) continue
    const result = up.data.debugResult?.result
    if (!result) continue
    const rows = result.dataset?.rows ?? []
    template[up.data.title] = { rows }
    normalized[up.id] = { rows }
  }
  mockInputRaw.value = JSON.stringify(template, null, 2)
  workflow.setNodeMockInputs(activeNode.value.id, normalized)
  mockInputError.value = undefined
}

/** 上游节点中是否有至少一个有上次 debug 结果可填充 */
const canFillFromLastRun = computed(() =>
  upstreamNodes.value.some(up => up?.data.debugResult?.result?.dataset?.rows?.length),
)

/** 用户编辑 mock 时，将节点 title 反查为 nodeId 存储 */
function onMockInputChange(e: Event) {
  const text = (e.target as HTMLTextAreaElement).value
  mockInputRaw.value = text
  mockInputError.value = undefined
  try {
    const parsed = JSON.parse(text)
    if (activeNode.value) {
      // 尝试将 title 转回 nodeId
      const graphStore = useWorkflowGraphStore()
      const titleToId: Record<string, string> = {}
      graphStore.nodes.forEach(n => { titleToId[n.data.title] = n.id })
      const normalized: Record<string, unknown> = {}
      for (const [key, val] of Object.entries(parsed)) {
        normalized[titleToId[key] ?? key] = val
      }
      workflow.setNodeMockInputs(activeNode.value.id, normalized)
    }
  }
  catch {
    mockInputError.value = 'JSON 格式错误'
  }
}

watch(() => activeNode.value?.data.mockInputs, (v) => {
  const graphStore = useWorkflowGraphStore()
  if (!v || Object.keys(v).length === 0) {
    mockInputRaw.value = '{}'
    return
  }
  const translated: Record<string, unknown> = {}
  for (const [nodeId, val] of Object.entries(v)) {
    const node = graphStore.getNodeById(nodeId)
    translated[node ? node.data.title : nodeId] = val
  }
  mockInputRaw.value = JSON.stringify(translated, null, 2)
}, { immediate: true })

async function requestChartRecommend() {
  if (chartRecommendLoading.value || upstreamFields.value.length === 0) return
  chartRecommendLoading.value = true
  chartRecommendError.value = ''
  try {
    chartRecommendations.value = await recommendChart({ fields: upstreamFields.value })
  }
  catch (err) {
    chartRecommendError.value = (err as Error).message || 'AI 推荐失败，请重试'
  }
  finally {
    chartRecommendLoading.value = false
  }
}

function applyChartRecommendation(rec: ChartRecommendationDTO) {
  const cleaned = Object.fromEntries(Object.entries(draft).filter(([k]) => k !== '__schema'))
  handleUpdate({ ...cleaned, chartType: rec.chartType })
  chartRecommendations.value = []
}
</script>

<template>
  <aside class="ncp" :style="{ '--cat': nodeCategoryColor }">
    <template v-if="activeNode && nodeData">
      <header class="ncp__header">
        <div class="ncp__header-left">
          <div class="ncp__cat-bar" />
          <div class="ncp__title-wrap">
            <div class="ncp__title-row">
              <input
                v-if="titleEditing"
                v-model="titleDraft"
                class="ncp__title-input"
                autofocus
                @blur="commitTitle"
                @keydown="onTitleKeydown"
              />
              <div v-else class="ncp__title" :title="'点击重命名'" @click="startEditTitle">{{ nodeData.title }}</div>
              <button v-if="!titleEditing" class="ncp__rename-btn" title="重命名节点" @click="startEditTitle">✎</button>
            </div>
            <div class="ncp__meta">
              <span v-if="nodeCategoryLabel" class="ncp__category">{{ nodeCategoryLabel }}</span>
              <span class="ncp__type">{{ nodeType }}</span>
            </div>
          </div>
        </div>
        <button class="ncp__run-btn" :class="{ 'ncp__run-btn--loading': isDebugLoading }" :disabled="isDebugLoading" title="运行此节点" @click="handleRunNode">
          <span v-if="isDebugLoading" class="ncp__run-spinner" />
          <span v-else>▷ 运行</span>
        </button>
      </header>

      <nav class="ncp__tabs">
        <button v-for="tab in ([{ key: 'config', label: '配置' }, { key: 'input', label: '输入' }, { key: 'output', label: '输出' }] as const)" :key="tab.key" class="ncp__tab" :class="{ 'ncp__tab--active': debugActiveTab === tab.key }" @click="workflow.setDebugTab(tab.key)">{{ tab.label }}</button>
      </nav>

      <div class="ncp__body">
        <template v-if="debugActiveTab === 'config'">
          <div v-if="schemaLoading" class="ncp__state"><span class="ncp__spinner" />配置加载中…</div>
          <div v-else-if="schemaError" class="ncp__state ncp__state--error">{{ schemaError }}</div>
          <div v-else-if="!schema" class="ncp__state">当前节点暂无配置</div>
          <FormRenderer v-else :schema="schema" :model-value="draft" :candidate-slots="candidateSlots" @update:model-value="handleUpdate" @valid="handleValid" />
          <div v-if="isCodeNode" class="ncp__code-summary">
            <div class="ncp__section-label">Code 摘要</div>
            <div class="ncp__code-grid">
              <div class="ncp__code-item"><span>语言</span><strong>{{ codeLanguage }}</strong></div>
              <div class="ncp__code-item"><span>输入</span><strong>{{ codeInputs.length ? codeInputs.slice(0, 4).join(', ') : '未配置' }}</strong></div>
              <div class="ncp__code-item"><span>输出</span><strong>{{ codeOutputs.length ? codeOutputs.slice(0, 4).join(', ') : '未声明' }}</strong></div>
              <div class="ncp__code-item ncp__code-item--full"><span>摘要</span><code>{{ codeSummary }}</code></div>
            </div>
          </div>
          <QueryActionsBar v-if="isSqlQueryNode && activeNode && schema && !schemaLoading" :node="activeNode" :datasource-id="String(draft.datasourceId ?? '')" :sql-template="String(draft.sqlTemplate ?? '')" :table-name="String(draft.tableId ?? '')" @sql-update="handleSqlUpdate" />
          <div v-if="isChartOutputNode" class="ncp__ai-recommend">
            <button class="ncp__ai-btn" :disabled="chartRecommendLoading || upstreamFields.length === 0" @click="requestChartRecommend">
              <span v-if="chartRecommendLoading" class="ncp__run-spinner" />
              <span v-else>✦</span>
              {{ chartRecommendLoading ? 'AI 推荐中...' : 'AI 推荐图表类型' }}
            </button>
            <div v-if="chartRecommendError" class="ncp__ai-error">{{ chartRecommendError }}</div>
            <div v-if="upstreamFields.length === 0 && !chartRecommendLoading" class="ncp__ai-hint">请先连接上游数据节点以获取字段信息</div>
            <div v-if="chartRecommendations.length > 0" class="ncp__ai-results">
              <AiRecommendBadge v-for="rec in chartRecommendations" :key="rec.chartType" :recommendation="rec" @accept="applyChartRecommendation(rec)" />
            </div>
          </div>
        </template>
        <template v-if="debugActiveTab === 'input'">
          <div class="ncp__mock">
            <div class="ncp__section-label">上游节点</div>
            <div v-if="upstreamNodes.length === 0" class="ncp__mock-hint">当前节点无上游连接</div>
            <div v-else class="ncp__upstream-list">
              <div v-for="up in upstreamNodes" :key="up!.id" class="ncp__upstream-item">
                <div class="ncp__upstream-header">
                  <span class="ncp__upstream-name">{{ up!.data.title }}</span>
                  <span class="ncp__upstream-type">{{ up!.data.meta?.displayName ?? up!.data.type }}</span>
                </div>
                <div v-if="up!.data.schema?.fields?.length" class="ncp__upstream-chips">
                  <span v-for="f in up!.data.schema!.fields" :key="f.name ?? f.fieldId" class="ncp__field-chip">
                    {{ f.name ?? f.fieldId }}
                  </span>
                </div>
                <span v-else class="ncp__upstream-no-schema">未运行（字段待推断）</span>
              </div>
            </div>

            <div class="ncp__mock-header">
              <div class="ncp__section-label">Mock 输入</div>
              <div v-if="upstreamNodes.length > 0" class="ncp__mock-actions">
                <button class="ncp__mock-btn" title="根据上游字段 schema 生成模板" @click="generateMockTemplate">
                  生成模板
                </button>
                <button
                  class="ncp__mock-btn"
                  :class="{ 'ncp__mock-btn--disabled': !canFillFromLastRun }"
                  :disabled="!canFillFromLastRun"
                  title="用上游节点上次运行的实际结果填充"
                  @click="fillFromLastRun"
                >
                  填充上次结果
                </button>
              </div>
            </div>

            <p class="ncp__mock-hint">
              格式：<code>{ "节点名称": { "rows": [{ "字段名": 值, ... }] } }</code>
            </p>
            <textarea class="ncp__mock-textarea" :value="mockInputRaw" spellcheck="false" placeholder="{}" @input="onMockInputChange" />
            <div v-if="mockInputError" class="ncp__mock-error">{{ mockInputError }}</div>
          </div>
        </template>
        <template v-if="debugActiveTab === 'output'">
          <NodeRunPanel :result="nodeData.debugResult" :loading="isDebugLoading" :node-type="nodeType" />
        </template>
      </div>
    </template>
    <div v-else class="ncp__empty">
      <div class="ncp__empty-icon">↗</div>
      <div>选择节点以查看配置</div>
    </div>
  </aside>
</template>

<style scoped>
.ncp { display: flex; flex-direction: column; width: 100%; min-width: 0; height: 100%; border-left: 1px solid var(--iap-divider); background: var(--iap-panel-bg); overflow: hidden; }
.ncp__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px 12px; border-bottom: 1px solid var(--iap-divider); flex-shrink: 0; background: var(--iap-panel-bg); }
.ncp__header-left { display: flex; align-items: center; gap: 10px; min-width: 0; }
.ncp__cat-bar { width: 3px; height: 32px; border-radius: 2px; background: var(--cat, #64748b); flex-shrink: 0; }
.ncp__title-wrap { min-width: 0; }
.ncp__title-row { display: flex; align-items: center; gap: 4px; min-width: 0; }
.ncp__title { font-size: 14px; font-weight: 700; color: var(--iap-text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }
.ncp__title:hover { color: var(--iap-text-accent); }
.ncp__title-input { font-size: 14px; font-weight: 700; color: var(--iap-text-primary); background: var(--iap-input-bg-focus, #f8fafc); border: 1px solid var(--iap-input-border-focus); border-radius: 6px; padding: 1px 6px; outline: none; min-width: 0; flex: 1; max-width: 180px; }
.ncp__rename-btn { display: grid; place-items: center; width: 18px; height: 18px; border: none; background: transparent; color: var(--iap-text-tertiary); cursor: pointer; border-radius: 4px; font-size: 12px; flex-shrink: 0; opacity: 0; transition: opacity 0.15s; padding: 0; }
.ncp__title-wrap:hover .ncp__rename-btn { opacity: 1; }
.ncp__rename-btn:hover { color: var(--iap-text-accent); background: color-mix(in srgb, var(--iap-text-accent) 10%, transparent); }
.ncp__meta { display: flex; align-items: center; gap: 6px; margin-top: 3px; }
.ncp__category { font-size: 10px; font-weight: 700; color: var(--cat, #64748b); background: color-mix(in srgb, var(--cat, #64748b) 12%, transparent); border: 1px solid color-mix(in srgb, var(--cat, #64748b) 30%, transparent); border-radius: 4px; padding: 1px 6px; text-transform: uppercase; letter-spacing: 0.05em; white-space: nowrap; }
.ncp__type { font-size: 11px; color: var(--iap-text-tertiary); font-family: 'JetBrains Mono', ui-monospace, monospace; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ncp__run-btn { display: flex; align-items: center; gap: 5px; padding: 6px 12px; border-radius: 8px; border: 1px solid color-mix(in srgb, var(--cat, #3b82f6) 40%, transparent); background: color-mix(in srgb, var(--cat, #3b82f6) 12%, transparent); color: var(--cat, #3b82f6); font-size: 12px; font-weight: 600; cursor: pointer; white-space: nowrap; transition: all 0.15s; flex-shrink: 0; }
.ncp__run-btn:hover:not(:disabled) { background: var(--cat, #3b82f6); color: #fff; border-color: transparent; }
.ncp__run-btn:disabled { opacity: 0.6; cursor: default; }
.ncp__run-spinner { display: inline-block; width: 11px; height: 11px; border: 2px solid rgba(255,255,255,0.2); border-top-color: currentColor; border-radius: 50%; animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.ncp__tabs { display: flex; border-bottom: 1px solid var(--iap-divider); flex-shrink: 0; background: var(--iap-panel-bg); }
.ncp__tab { flex: 1; padding: 9px 0; font-size: 12px; font-weight: 500; color: var(--iap-text-tertiary); background: transparent; border: none; border-bottom: 2px solid transparent; cursor: pointer; transition: color 0.15s, border-color 0.15s; letter-spacing: 0.02em; }
.ncp__tab:hover { color: var(--iap-text-secondary); }
.ncp__tab--active { color: var(--cat, #3b82f6); border-bottom-color: var(--cat, #3b82f6); font-weight: 600; }
.ncp__body { flex: 1; overflow-y: auto; padding: 14px 16px; display: flex; flex-direction: column; gap: 14px; }
.ncp__state { display: flex; align-items: center; gap: 8px; color: var(--iap-text-tertiary); font-size: 13px; padding: 8px 0; }
.ncp__state--error { color: var(--iap-error-text); }
.ncp__spinner { display: inline-block; width: 12px; height: 12px; border: 2px solid var(--iap-divider-strong); border-top-color: var(--iap-text-accent); border-radius: 50%; animation: spin 0.7s linear infinite; }
.ncp__mock { display: flex; flex-direction: column; gap: 8px; }
.ncp__section-label { font-size: 10px; font-weight: 700; color: var(--iap-text-tertiary); letter-spacing: 0.08em; text-transform: uppercase; }
.ncp__mock-header { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; }
.ncp__mock-actions { display: flex; gap: 6px; }
.ncp__mock-btn { display: inline-flex; align-items: center; padding: 3px 10px; border-radius: 6px; border: 1px solid var(--iap-divider); background: var(--iap-surface-secondary); color: var(--iap-text-secondary); font-size: 11px; cursor: pointer; transition: background 0.12s; white-space: nowrap; }
.ncp__mock-btn:hover:not(:disabled) { background: var(--iap-hover-bg); color: var(--iap-text-primary); }
.ncp__mock-btn--disabled { opacity: 0.4; cursor: not-allowed; }
.ncp__mock-hint { font-size: 11px; color: var(--iap-text-tertiary); line-height: 1.6; margin: 0; }
.ncp__mock-hint code { font-family: 'JetBrains Mono', ui-monospace, monospace; color: var(--iap-text-accent); background: color-mix(in srgb, var(--iap-text-accent) 10%, transparent); border-radius: 3px; padding: 1px 4px; }
.ncp__mock-textarea { width: 100%; min-height: 160px; resize: vertical; background: var(--iap-code-bg); border: 1px solid var(--iap-input-border); border-radius: 8px; padding: 10px 12px; font-family: 'JetBrains Mono', ui-monospace, monospace; font-size: 12px; color: var(--iap-text-secondary); line-height: 1.6; outline: none; transition: border-color 0.15s, box-shadow 0.15s; box-sizing: border-box; }
.ncp__mock-textarea:focus { border-color: var(--iap-input-border-focus); box-shadow: 0 0 0 3px var(--iap-accent-ring); }
.ncp__mock-error { font-size: 11px; color: var(--iap-error-text); }
.ncp__upstream-list { display: flex; flex-direction: column; gap: 8px; }
.ncp__upstream-item { padding: 10px 12px; border: 1px solid var(--iap-divider); border-radius: 10px; background: var(--iap-surface-secondary); display: flex; flex-direction: column; gap: 6px; }
.ncp__upstream-header { display: flex; align-items: center; gap: 8px; }
.ncp__upstream-name { font-size: 12px; font-weight: 600; color: var(--iap-text-primary); }
.ncp__upstream-type { font-size: 10px; color: var(--iap-text-tertiary); font-family: 'JetBrains Mono', ui-monospace, monospace; background: color-mix(in srgb, var(--cat) 10%, var(--iap-surface-secondary)); border: 1px solid color-mix(in srgb, var(--cat) 20%, transparent); border-radius: 4px; padding: 1px 6px; }
.ncp__upstream-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.ncp__field-chip { display: inline-flex; align-items: center; font-size: 11px; font-family: 'JetBrains Mono', ui-monospace, monospace; color: var(--iap-text-accent); background: color-mix(in srgb, var(--iap-text-accent) 10%, transparent); border: 1px solid color-mix(in srgb, var(--iap-text-accent) 22%, transparent); border-radius: 4px; padding: 1px 6px; white-space: nowrap; }
.ncp__upstream-no-schema { font-size: 11px; color: var(--iap-text-disabled); font-style: italic; }
.ncp__empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: var(--iap-text-placeholder); font-size: 13px; }
.ncp__empty-icon { font-size: 28px; color: var(--iap-text-disabled); }
.ncp__code-summary { display: flex; flex-direction: column; gap: 8px; padding: 12px; border: 1px solid var(--iap-divider); border-radius: 10px; background: var(--iap-code-bg); }
.ncp__code-grid { display: grid; gap: 8px; }
.ncp__code-item { display: grid; gap: 4px; }
.ncp__code-item span { font-size: 11px; color: var(--iap-text-tertiary); }
.ncp__code-item strong, .ncp__code-item code { color: var(--iap-text-primary); font-size: 12px; }
.ncp__code-item code { font-family: 'JetBrains Mono', ui-monospace, monospace; white-space: pre-wrap; word-break: break-word; }
.ncp__code-item--full { padding-top: 4px; border-top: 1px solid var(--iap-divider); }
.ncp__ai-recommend { display: flex; flex-direction: column; gap: 8px; }
.ncp__ai-btn { display: inline-flex; align-items: center; gap: 6px; padding: 7px 14px; border-radius: 8px; border: 1px solid var(--iap-ai-btn-border); background: var(--iap-ai-btn-bg); color: var(--iap-ai-btn-text); font-size: 12px; cursor: pointer; transition: background 0.15s; }
.ncp__ai-btn:hover:not(:disabled) { background: var(--iap-ai-btn-hover); }
.ncp__ai-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ncp__ai-error { font-size: 11px; color: var(--iap-error-text); }
.ncp__ai-hint { font-size: 11px; color: var(--iap-text-tertiary); }
.ncp__ai-results { display: flex; flex-direction: column; gap: 6px; }
</style>
