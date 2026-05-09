<script setup lang="ts">
import { computed } from 'vue'
import FormRenderer from '@/components/form/FormRenderer.vue'
import NodeDebugPanel from '@/components/debug/NodeDebugPanel.vue'
import type { WorkflowNode } from '@/types/workflow'
import { usePanelController } from '@/composables/usePanelController'

const props = defineProps<{
  node?: WorkflowNode
}>()

const activeNode = computed(() => props.node)
const nodeData = computed(() => activeNode.value?.data)
const { draft, meta, schema, schemaLoading, schemaError, candidateSlots, handleUpdate, handleValid } = usePanelController(activeNode)

const categoryLabel: Record<string, string> = {
  QUERY: '取数',
  COMPUTE: '计算',
  OUTPUT: '输出',
  GOVERNANCE: '治理',
}

const categoryColor: Record<string, string> = {
  QUERY: '#3b82f6',
  COMPUTE: '#8b5cf6',
  OUTPUT: '#10b981',
  GOVERNANCE: '#f59e0b',
}

const nodeCategoryLabel = computed(() => categoryLabel[meta.value?.category ?? ''] ?? meta.value?.category ?? '')
const nodeCategoryColor = computed(() => categoryColor[meta.value?.category ?? ''] ?? '#64748b')
</script>

<template>
  <aside class="node-config-panel">
    <template v-if="activeNode && nodeData">
      <header class="node-config-panel__header">
        <div class="node-config-panel__header-left">
          <div class="node-config-panel__title">{{ nodeData.title }}</div>
          <div class="node-config-panel__meta">
            <span
              v-if="nodeCategoryLabel"
              class="node-config-panel__category"
              :style="{ '--cat-color': nodeCategoryColor }"
            >{{ nodeCategoryLabel }}</span>
            <span class="node-config-panel__type">{{ nodeData.nodeType }}</span>
          </div>
        </div>
        <div class="node-config-panel__status" :class="`is-${nodeData.status}`">{{ nodeData.status }}</div>
      </header>

      <div v-if="schemaLoading" class="node-config-panel__state">
        <span class="node-config-panel__spinner" />配置加载中…
      </div>
      <div v-else-if="schemaError" class="node-config-panel__state node-config-panel__state--error">
        {{ schemaError }}
      </div>
      <div v-else-if="!schema" class="node-config-panel__state">当前节点暂无配置</div>
      <template v-else>
        <FormRenderer
          :schema="schema"
          :model-value="draft"
          :candidate-slots="candidateSlots"
          @update:model-value="handleUpdate"
          @valid="handleValid"
        />
        <slot name="extension" :node="activeNode" :meta="meta" :draft="draft" />
      </template>
      <NodeDebugPanel :node="activeNode" />
    </template>
    <div v-else class="node-config-panel__empty">
      <div class="node-config-panel__empty-icon">↗</div>
      <div>选择节点以查看配置</div>
    </div>
  </aside>
</template>

<style scoped>
.node-config-panel {
  padding: 16px;
  border-left: 1px solid #1e293b;
  background: #020617;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.node-config-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #1e293b;
}

.node-config-panel__header-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.node-config-panel__title {
  font-size: 16px;
  font-weight: 700;
  color: #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-config-panel__meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-config-panel__category {
  font-size: 11px;
  font-weight: 600;
  color: var(--cat-color, #64748b);
  background: color-mix(in srgb, var(--cat-color, #64748b) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--cat-color, #64748b) 30%, transparent);
  border-radius: 6px;
  padding: 1px 7px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.node-config-panel__type {
  font-size: 12px;
  color: #64748b;
  font-family: 'SFMono-Regular', ui-monospace, monospace;
}

.node-config-panel__status {
  flex-shrink: 0;
  border: 1px solid #334155;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
  white-space: nowrap;
}

.node-config-panel__status.is-error {
  color: #fca5a5;
  border-color: #7f1d1d;
  background: rgba(127, 29, 29, 0.15);
}

.node-config-panel__status.is-valid,
.node-config-panel__status.is-success {
  color: #86efac;
  border-color: #14532d;
  background: rgba(20, 83, 45, 0.15);
}

.node-config-panel__status.is-draft,
.node-config-panel__status.is-running {
  color: #fcd34d;
  border-color: #713f12;
  background: rgba(113, 63, 18, 0.15);
}

.node-config-panel__state {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
  padding: 8px 0;
}

.node-config-panel__state--error {
  color: #fca5a5;
}

.node-config-panel__spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid #1e293b;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.node-config-panel__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #334155;
  font-size: 13px;
}

.node-config-panel__empty-icon {
  font-size: 28px;
  color: #1e293b;
}
</style>
