<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useNodeRegistryStore } from '@/stores/node-registry'
import type { NodeMetaDTO } from '@/types/contract'

const emit = defineEmits<{
  add: [meta: NodeMetaDTO]
}>()

const registry = useNodeRegistryStore()
const { sortedNodes, loading, error } = storeToRefs(registry)

const categoryMeta: Record<string, { label: string; color: string }> = {
  QUERY: { label: '取数', color: '#3b82f6' },
  COMPUTE: { label: '计算', color: '#8b5cf6' },
  OUTPUT: { label: '输出', color: '#10b981' },
  GOVERNANCE: { label: '治理', color: '#f59e0b' },
}

const groupedNodes = computed(() => {
  const groups: Record<string, NodeMetaDTO[]> = {}
  for (const node of sortedNodes.value) {
    const cat = node.category ?? 'OTHER'
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(node)
  }
  return Object.entries(groups).map(([category, nodes]) => ({
    category,
    meta: categoryMeta[category] ?? { label: category, color: '#64748b' },
    nodes,
  }))
})
</script>

<template>
  <aside class="node-palette">
    <div class="node-palette__header">
      <div class="node-palette__title">节点库</div>
    </div>
    <div v-if="loading" class="node-palette__state">
      <span class="node-palette__spinner" />加载中…
    </div>
    <div v-else-if="error" class="node-palette__state node-palette__state--error">{{ error }}</div>
    <template v-else>
      <div v-for="group in groupedNodes" :key="group.category" class="node-palette__group">
        <div class="node-palette__category" :style="{ '--cat-color': group.meta.color }">
          {{ group.meta.label }}
        </div>
        <button
          v-for="meta in group.nodes"
          :key="meta.nodeType"
          class="node-palette__item"
          :style="{ '--cat-color': group.meta.color }"
          :title="meta.description ?? meta.displayName"
          @click="emit('add', meta)"
        >
          <span class="node-palette__item-dot" />
          <span class="node-palette__item-name">{{ meta.displayName }}</span>
        </button>
      </div>
    </template>
  </aside>
</template>

<style scoped>
.node-palette {
  padding: 16px 12px;
  border-right: 1px solid #1e293b;
  background: #020617;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
}

.node-palette__header {
  padding: 0 4px 8px;
  border-bottom: 1px solid #1e293b;
  margin-bottom: 6px;
}

.node-palette__title {
  font-size: 14px;
  font-weight: 700;
  color: #e2e8f0;
  letter-spacing: 0.03em;
}

.node-palette__group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node-palette__category {
  font-size: 11px;
  font-weight: 600;
  color: var(--cat-color, #64748b);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 8px 8px 4px;
}

.node-palette__item {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: #cbd5e1;
  padding: 9px 10px;
  text-align: left;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.12s, border-color 0.12s;
}

.node-palette__item:hover {
  background: rgba(255, 255, 255, 0.04);
  border-color: #1e293b;
  color: #f1f5f9;
}

.node-palette__item-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cat-color, #64748b);
  opacity: 0.8;
}

.node-palette__item:hover .node-palette__item-dot {
  opacity: 1;
}

.node-palette__item-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-palette__state {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
  padding: 12px 8px;
}

.node-palette__state--error {
  color: #fca5a5;
}

.node-palette__spinner {
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
</style>
