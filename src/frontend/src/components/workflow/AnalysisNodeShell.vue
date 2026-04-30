<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { WorkflowNodeData } from '@/types/workflow'
import { resolveNodeIcon } from '@/utils/node-preview'

const props = defineProps<{
  data: WorkflowNodeData
}>()

const statusTone = computed(() => ({
  idle: '#64748b',
  draft: '#f59e0b',
  valid: '#38bdf8',
  running: '#a855f7',
  success: '#22c55e',
  error: '#ef4444',
}[props.data.status]))
</script>

<template>
  <div class="analysis-node-shell">
    <Handle id="input" type="target" :position="Position.Left" />
    <div class="analysis-node-shell__header">
      <div class="analysis-node-shell__icon">{{ resolveNodeIcon(data.meta) }}</div>
      <div>
        <div class="analysis-node-shell__title">{{ data.title }}</div>
        <div class="analysis-node-shell__type">{{ data.nodeType }}</div>
      </div>
      <div class="analysis-node-shell__status" :style="{ background: statusTone }">{{ data.status }}</div>
    </div>
    <div class="analysis-node-shell__preview">
      <div v-for="line in data.preview" :key="line">{{ line }}</div>
    </div>
    <div class="analysis-node-shell__ports">
      <span>in {{ data.meta?.inputPorts?.length ?? 0 }}</span>
      <span>out {{ data.meta?.outputPorts?.length ?? 0 }}</span>
    </div>
    <Handle id="output" type="source" :position="Position.Right" />
  </div>
</template>

<style scoped>
.analysis-node-shell {
  min-width: 240px;
  padding: 14px;
  border: 1px solid #334155;
  border-radius: 16px;
  background: linear-gradient(180deg, #111827, #0f172a);
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.35);
}
.analysis-node-shell__header {
  display: grid;
  grid-template-columns: 36px 1fr auto;
  gap: 10px;
  align-items: center;
}
.analysis-node-shell__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: #1e293b;
  font-weight: 700;
}
.analysis-node-shell__title {
  font-weight: 700;
}
.analysis-node-shell__type {
  font-size: 12px;
  color: #94a3b8;
}
.analysis-node-shell__status {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
}
.analysis-node-shell__preview {
  margin-top: 12px;
  padding: 10px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.75);
  color: #cbd5e1;
  font-size: 12px;
  display: grid;
  gap: 4px;
}
.analysis-node-shell__ports {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  color: #94a3b8;
  font-size: 12px;
}
</style>
