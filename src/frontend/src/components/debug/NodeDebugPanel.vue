<script setup lang="ts">
import { computed, watch } from 'vue'
import TablePreview from '@/components/output/TablePreview.vue'
import ChartPreview from '@/components/output/ChartPreview.vue'
import { getBusinessNodeType } from '@/adapters/workflow-graph'
import { useWorkflowStore } from '@/stores/workflow'
import type { QueryResultDTO, StandardResultDTO } from '@/types/contract'
import type { WorkflowNode } from '@/types/workflow'
import { useMappingCandidates } from '@/composables/useMappingCandidates'
import { useNodeDebug } from '@/composables/useNodeDebug'

const props = defineProps<{
  node: WorkflowNode
}>()

const workflow = useWorkflowStore()
const debug = useNodeDebug()
const activeNode = computed(() => props.node)
const nodeData = computed(() => activeNode.value.data)
const businessNodeType = computed(() => getBusinessNodeType(activeNode.value))
const validationResult = computed(() => debug.validation.value)
const previewResult = computed(() => debug.preview.value)
const previewRendererResult = computed<StandardResultDTO | QueryResultDTO | undefined>(() => {
  return previewResult.value
})
const { loadCandidates } = useMappingCandidates(activeNode)

// Check if SQL Query node has required configuration
const canDebugSqlQuery = computed(() => {
  if (businessNodeType.value !== 'sql_query') return true
  const config = nodeData.value.config
  const hasDatasource = config.datasourceId && String(config.datasourceId).trim() !== ''
  const hasSql = config.sqlTemplate && String(config.sqlTemplate).trim() !== ''
  return hasDatasource && hasSql
})

const debugDisabledReason = computed(() => {
  if (businessNodeType.value !== 'sql_query') return ''
  const config = nodeData.value.config
  if (!config.datasourceId || String(config.datasourceId).trim() === '') {
    return '请先选择数据源'
  }
  if (!config.sqlTemplate || String(config.sqlTemplate).trim() === '') {
    return '请先输入SQL语句'
  }
  return ''
})

watch(() => nodeData.value.config.chartType, () => {
  loadCandidates().catch(() => undefined)
})

async function handleValidate() {
  if (!canDebugSqlQuery.value) return
  await debug.runValidate(activeNode.value)
  workflow.updateNodeStatus(activeNode.value.id, validationResult.value?.valid ? 'valid' : 'error')
}

async function handlePreview() {
  if (!canDebugSqlQuery.value) return
  const result = await debug.runPreview(activeNode.value)
  const nextStatus = result.status === 'SUCCEEDED'
    ? 'success'
    : result.status === 'FAILED' || result.status === 'CANCELLED'
      ? 'error'
      : 'running'
  workflow.updateNodeStatus(activeNode.value.id, nextStatus)
}

async function handleSchema() {
  const schema = await debug.runSchemaInfer(activeNode.value)
  workflow.updateNodeSchema(activeNode.value.id, schema)
  workflow.updateNodeStatus(activeNode.value.id, 'valid')
  await loadCandidates()
}
</script>

<template>
  <section class="node-debug-panel">
    <header class="node-debug-panel__header">调试</header>
    <div v-if="debugDisabledReason" class="node-debug-panel__warning">
      {{ debugDisabledReason }}
    </div>
    <div class="node-debug-panel__actions">
      <button
        v-if="businessNodeType === 'sql_query'"
        :disabled="!canDebugSqlQuery || debug.loading"
        :title="debugDisabledReason"
        @click="handleValidate"
      >
        {{ debug.loading ? '校验中...' : 'Validate' }}
      </button>
      <button
        v-if="businessNodeType === 'sql_query'"
        :disabled="!canDebugSqlQuery || debug.loading"
        :title="debugDisabledReason"
        @click="handlePreview"
      >
        {{ debug.loading ? '预览中...' : 'Preview' }}
      </button>
      <button
        :disabled="debug.loading"
        @click="handleSchema"
      >
        {{ debug.loading ? '推断中...' : 'Schema' }}
      </button>
    </div>
    <div v-if="debug.error" class="node-debug-panel__error">{{ debug.error }}</div>
    <div v-if="validationResult" class="node-debug-panel__block">
      <strong>校验结果</strong>
      <span>{{ validationResult.valid ? '通过' : validationResult.message }}</span>
    </div>
    <div v-if="nodeData.schema?.fields?.length" class="node-debug-panel__block">
      <strong>Schema</strong>
      <span>{{ nodeData.schema.fields.map(item => item.name).join(', ') }}</span>
    </div>
    <TablePreview v-if="previewRendererResult && businessNodeType !== 'chart_output'" :result="previewRendererResult" mode="preview" />
    <ChartPreview v-if="businessNodeType === 'chart_output'" :result="previewRendererResult ?? { kind: 'EMPTY' }" mode="preview" />
  </section>
</template>

<style scoped>
.node-debug-panel {
  padding: 16px;
  border: 1px solid #1e293b;
  border-radius: 16px;
  background: #0f172a;
  display: grid;
  gap: 12px;
}
.node-debug-panel__header {
  font-weight: 700;
}
.node-debug-panel__warning {
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(251, 191, 36, 0.1);
  border: 1px solid rgba(251, 191, 36, 0.3);
  color: #fbbf24;
  font-size: 12px;
}
.node-debug-panel__actions {
  display: flex;
  gap: 8px;
}
.node-debug-panel__actions button {
  border: 1px solid #334155;
  border-radius: 10px;
  background: #111827;
  color: inherit;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.node-debug-panel__actions button:hover:not(:disabled) {
  background: #1e293b;
  border-color: #475569;
}
.node-debug-panel__actions button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.node-debug-panel__block {
  display: grid;
  gap: 6px;
  color: #cbd5e1;
  font-size: 12px;
}
.node-debug-panel__error {
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #fca5a5;
  font-size: 12px;
}
</style>
