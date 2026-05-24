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

watch(() => nodeData.value.config.chartType, () => {
  loadCandidates().catch(() => undefined)
})

async function handleValidate() {
  await debug.runValidate(activeNode.value)
  workflow.updateNodeStatus(activeNode.value.id, validationResult.value?.valid ? 'valid' : 'error')
}

async function handlePreview() {
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
    <div class="node-debug-panel__actions">
      <button v-if="businessNodeType === 'sql_query'" @click="handleValidate">Validate</button>
      <button v-if="businessNodeType === 'sql_query'" @click="handlePreview">Preview</button>
      <button @click="handleSchema">Schema</button>
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
}
.node-debug-panel__block {
  display: grid;
  gap: 6px;
  color: #cbd5e1;
  font-size: 12px;
}
.node-debug-panel__error {
  color: #fca5a5;
  font-size: 12px;
}
</style>
