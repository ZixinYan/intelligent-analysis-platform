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
</script>

<template>
  <aside class="node-config-panel">
    <template v-if="activeNode && nodeData">
      <header class="node-config-panel__header">
        <div>
          <div class="node-config-panel__title">{{ nodeData.title }}</div>
          <div class="node-config-panel__type">{{ nodeData.nodeType }}</div>
        </div>
        <div class="node-config-panel__status" :class="`is-${nodeData.status}`">{{ nodeData.status }}</div>
      </header>
      <div v-if="schemaLoading" class="node-config-panel__state">配置加载中...</div>
      <div v-else-if="schemaError" class="node-config-panel__state node-config-panel__state--error">{{ schemaError }}</div>
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
    <div v-else class="node-config-panel__empty">请选择节点</div>
  </aside>
</template>

<style scoped>
.node-config-panel {
  padding: 16px;
  border-left: 1px solid #1e293b;
  background: #020617;
  overflow: auto;
  display: grid;
  gap: 16px;
}
.node-config-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.node-config-panel__title {
  font-size: 18px;
  font-weight: 700;
}
.node-config-panel__type,
.node-config-panel__empty,
.node-config-panel__state {
  color: #94a3b8;
}
.node-config-panel__status {
  border: 1px solid #334155;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  text-transform: uppercase;
}
.node-config-panel__status.is-error {
  color: #fca5a5;
  border-color: #7f1d1d;
}
.node-config-panel__status.is-valid,
.node-config-panel__status.is-success {
  color: #86efac;
  border-color: #14532d;
}
.node-config-panel__status.is-draft,
.node-config-panel__status.is-running {
  color: #fcd34d;
  border-color: #713f12;
}
.node-config-panel__state--error {
  color: #fca5a5;
}
</style>
