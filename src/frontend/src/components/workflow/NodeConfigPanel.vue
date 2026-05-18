<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import FormRenderer from '@/components/form/FormRenderer.vue'
import NodeRunPanel from '@/components/workflow/NodeRunPanel.vue'
import QueryActionsBar from '@/components/query/QueryActionsBar.vue'
import AiRecommendBadge from '@/components/ai/AiRecommendBadge.vue'
import type { WorkflowNode } from '@/types/workflow'
import { usePanelController } from '@/composables/usePanelController'
import { useWorkflowStore } from '@/stores/workflow'
import { recommendChart } from '@/api/ai'
import type { ChartRecommendationDTO } from '@/types/contract'

const props = defineProps<{
  node?: WorkflowNode
}>()

const workflow = useWorkflowStore()
const { debugActiveTab, debugLoadingNodeId } = storeToRefs(workflow)

const activeNode = computed(() => props.node)
const nodeData = computed(() => activeNode.value?.data)
const { draft, meta, schema, schemaLoading, schemaError, candidateSlots, handleUpdate, handleValid } = usePanelController(activeNode)

const categoryLabel: Record<string, string> = {
  QUERY: '取数', COMPUTE: '计算', OUTPUT: '输出', GOVERNANCE: '治理', ANALYSIS: '分析',
}
const categoryColor: Record<string, string> = {
  QUERY: '#3b82f6', COMPUTE: '#8b5cf6', OUTPUT: '#10b981', GOVERNANCE: '#f59e0b', ANALYSIS: '#06b6d4',
}

const nodeCategoryLabel = computed(() => categoryLabel[meta.value?.category ?? ''] ?? meta.value?.category ?? '')
const nodeCategoryColor = computed(() => categoryColor[meta.value?.category ?? ''] ?? '#64748b')

const mockInputRaw = ref('{}')
const mockInputError = ref<string>()

watch(() => activeNode.value?.data.mockInputs, (v) => {
  mockInputRaw.value = v ? JSON.stringify(v, null, 2) : '{}'
}, { immediate: true })

function onMockInputChange(e: Event) {
  const text = (e.target as HTMLTextAreaElement).value
  mockInputRaw.value = text
  mockInputError.value = undefined
  try {
    const parsed = JSON.parse(text)
    if (activeNode.value) {
      workflow.setNodeMockInputs(activeNode.value.id, parsed)
    }
  }
  catch {
    mockInputError.value = 'JSON 格式错误'
  }
}

const isDebugLoading = computed(() =>
  activeNode.value ? debugLoadingNodeId.value === activeNode.value.id : false,
)

function handleRunNode() {
  if (activeNode.value) {
    workflow.runNodeDebug(activeNode.value.id)
  }
}

const SQL_QUERY_TYPES = new Set(['analysis-sql-query', 'sql_query'])
const isSqlQueryNode = computed(() => SQL_QUERY_TYPES.has(activeNode.value?.data.nodeType ?? ''))

function handleSqlUpdate(sql: string) {
  const cleaned = Object.fromEntries(
    Object.entries(draft).filter(([k]) => k !== '__schema'),
  )
  handleUpdate({ ...cleaned, sqlTemplate: sql })
}

const CHART_OUTPUT_TYPES = new Set(['analysis-chart-output', 'chart_output'])
const isChartOutputNode = computed(() => CHART_OUTPUT_TYPES.has(activeNode.value?.data.nodeType ?? ''))

const chartRecommendations = ref<ChartRecommendationDTO[]>([])
const chartRecommendLoading = ref(false)
const chartRecommendError = ref('')

watch(() => activeNode.value?.id, () => {
  chartRecommendations.value = []
  chartRecommendError.value = ''
})

const upstreamFields = computed(() => {
  if (!activeNode.value) return []
  const upstream = workflow.getUpstreamNode(activeNode.value.id)
  return upstream?.data.schema?.fields ?? []
})

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
  const cleaned = Object.fromEntries(
    Object.entries(draft).filter(([k]) => k !== '__schema'),
  )
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
            <div class="ncp__title">{{ nodeData.title }}</div>
            <div class="ncp__meta">
              <span v-if="nodeCategoryLabel" class="ncp__category">{{ nodeCategoryLabel }}</span>
              <span class="ncp__type">{{ nodeData.nodeType }}</span>
            </div>
          </div>
        </div>
        <button
          class="ncp__run-btn"
          :class="{ 'ncp__run-btn--loading': isDebugLoading }"
          :disabled="isDebugLoading"
          title="运行此节点"
          @click="handleRunNode"
        >
          <span v-if="isDebugLoading" class="ncp__run-spinner" />
          <span v-else>▷ 运行</span>
        </button>
      </header>

      <nav class="ncp__tabs">
        <button
          v-for="tab in ([
            { key: 'config', label: '配置' },
            { key: 'input',  label: '输入' },
            { key: 'output', label: '输出' },
          ] as const)"
          :key="tab.key"
          class="ncp__tab"
          :class="{ 'ncp__tab--active': debugActiveTab === tab.key }"
          @click="workflow.setDebugTab(tab.key)"
        >{{ tab.label }}</button>
      </nav>

      <div class="ncp__body">
        <template v-if="debugActiveTab === 'config'">
          <div v-if="schemaLoading" class="ncp__state">
            <span class="ncp__spinner" />配置加载中…
          </div>
          <div v-else-if="schemaError" class="ncp__state ncp__state--error">{{ schemaError }}</div>
          <div v-else-if="!schema" class="ncp__state">当前节点暂无配置</div>
          <FormRenderer
            v-else
            :schema="schema"
            :model-value="draft"
            :candidate-slots="candidateSlots"
            @update:model-value="handleUpdate"
            @valid="handleValid"
          />
          <QueryActionsBar
            v-if="isSqlQueryNode && activeNode && schema && !schemaLoading"
            :node="activeNode!"
            :datasource-id="String(draft.datasourceId ?? '')"
            :sql-template="String(draft.sqlTemplate ?? '')"
            :table-name="String(draft.tableId ?? '')"
            @sql-update="handleSqlUpdate"
          />

          <div v-if="isChartOutputNode" class="ncp__ai-recommend">
            <button
              class="ncp__ai-btn"
              :disabled="chartRecommendLoading || upstreamFields.length === 0"
              @click="requestChartRecommend"
            >
              <span v-if="chartRecommendLoading" class="ncp__run-spinner" />
              <span v-else>✦</span>
              {{ chartRecommendLoading ? 'AI 推荐中...' : 'AI 推荐图表类型' }}
            </button>
            <div v-if="chartRecommendError" class="ncp__ai-error">{{ chartRecommendError }}</div>
            <div v-if="upstreamFields.length === 0 && !chartRecommendLoading" class="ncp__ai-hint">
              请先连接上游数据节点以获取字段信息
            </div>
            <div v-if="chartRecommendations.length > 0" class="ncp__ai-results">
              <AiRecommendBadge
                v-for="rec in chartRecommendations"
                :key="rec.chartType"
                :recommendation="rec"
                @accept="applyChartRecommendation(rec)"
              />
            </div>
          </div>
        </template>

        <template v-if="debugActiveTab === 'input'">
          <div class="ncp__mock">
            <div class="ncp__section-label">上游 Mock 输入</div>
            <p class="ncp__mock-hint">
              填写 JSON，模拟上游节点的输出，格式为
              <code>{ "nodeId": { "rows": [...] } }</code>
            </p>
            <textarea
              class="ncp__mock-textarea"
              :value="mockInputRaw"
              spellcheck="false"
              placeholder="{}"
              @input="onMockInputChange"
            />
            <div v-if="mockInputError" class="ncp__mock-error">{{ mockInputError }}</div>
          </div>
        </template>

        <template v-if="debugActiveTab === 'output'">
          <NodeRunPanel
            :result="nodeData.debugResult"
            :loading="isDebugLoading"
          />
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
.ncp {
  display: flex;
  flex-direction: column;
  height: 100%;
  border-left: 1px solid var(--iap-divider);
  background: var(--iap-panel-bg);
  overflow: hidden;
}

.ncp__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--iap-divider);
  flex-shrink: 0;
  background: var(--iap-panel-bg);
}
.ncp__header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.ncp__cat-bar {
  width: 3px;
  height: 32px;
  border-radius: 2px;
  background: var(--cat, #64748b);
  flex-shrink: 0;
}
.ncp__title-wrap { min-width: 0; }
.ncp__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--iap-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ncp__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
}
.ncp__category {
  font-size: 10px;
  font-weight: 700;
  color: var(--cat, #64748b);
  background: color-mix(in srgb, var(--cat, #64748b) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--cat, #64748b) 30%, transparent);
  border-radius: 4px;
  padding: 1px 6px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  white-space: nowrap;
}
.ncp__type {
  font-size: 11px;
  color: var(--iap-text-tertiary);
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ncp__run-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--cat, #3b82f6) 40%, transparent);
  background: color-mix(in srgb, var(--cat, #3b82f6) 12%, transparent);
  color: var(--cat, #3b82f6);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  flex-shrink: 0;
}
.ncp__run-btn:hover:not(:disabled) {
  background: var(--cat, #3b82f6);
  color: #fff;
  border-color: transparent;
}
.ncp__run-btn:disabled { opacity: 0.6; cursor: default; }
.ncp__run-spinner {
  display: inline-block;
  width: 11px;
  height: 11px;
  border: 2px solid rgba(255,255,255,0.2);
  border-top-color: currentColor;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.ncp__tabs {
  display: flex;
  border-bottom: 1px solid var(--iap-divider);
  flex-shrink: 0;
  background: var(--iap-panel-bg);
}
.ncp__tab {
  flex: 1;
  padding: 9px 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--iap-text-tertiary);
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
  letter-spacing: 0.02em;
}
.ncp__tab:hover { color: var(--iap-text-secondary); }
.ncp__tab--active {
  color: var(--cat, #3b82f6);
  border-bottom-color: var(--cat, #3b82f6);
  font-weight: 600;
}

.ncp__body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ncp__state {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--iap-text-tertiary);
  font-size: 13px;
  padding: 8px 0;
}
.ncp__state--error { color: var(--iap-error-text); }
.ncp__spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid var(--iap-divider-strong);
  border-top-color: var(--iap-text-accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.ncp__mock { display: flex; flex-direction: column; gap: 8px; }
.ncp__section-label {
  font-size: 10px;
  font-weight: 700;
  color: var(--iap-text-tertiary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.ncp__mock-hint {
  font-size: 11px;
  color: var(--iap-text-tertiary);
  line-height: 1.6;
  margin: 0;
}
.ncp__mock-hint code {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  color: var(--iap-text-accent);
  background: color-mix(in srgb, var(--iap-text-accent) 10%, transparent);
  border-radius: 3px;
  padding: 1px 4px;
}
.ncp__mock-textarea {
  width: 100%;
  min-height: 160px;
  resize: vertical;
  background: var(--iap-code-bg);
  border: 1px solid var(--iap-input-border);
  border-radius: 8px;
  padding: 10px 12px;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 12px;
  color: var(--iap-text-secondary);
  line-height: 1.6;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  box-sizing: border-box;
}
.ncp__mock-textarea:focus {
  border-color: var(--iap-input-border-focus);
  box-shadow: 0 0 0 3px var(--iap-accent-ring);
}
.ncp__mock-error {
  font-size: 11px;
  color: var(--iap-error-text);
}

.ncp__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--iap-text-placeholder);
  font-size: 13px;
}
.ncp__empty-icon { font-size: 28px; color: var(--iap-text-disabled); }

.ncp__ai-recommend { display: flex; flex-direction: column; gap: 8px; }
.ncp__ai-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 8px;
  border: 1px solid var(--iap-ai-btn-border);
  background: var(--iap-ai-btn-bg);
  color: var(--iap-ai-btn-text);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s;
}
.ncp__ai-btn:hover:not(:disabled) { background: var(--iap-ai-btn-hover); }
.ncp__ai-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ncp__ai-error { font-size: 11px; color: var(--iap-error-text); }
.ncp__ai-hint { font-size: 11px; color: var(--iap-text-tertiary); }
.ncp__ai-results { display: flex; flex-direction: column; gap: 6px; }
</style>
