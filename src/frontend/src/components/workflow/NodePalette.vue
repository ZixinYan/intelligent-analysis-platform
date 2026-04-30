<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useNodeRegistryStore } from '@/stores/node-registry'
import type { NodeMetaDTO } from '@/types/contract'

const emit = defineEmits<{
  add: [meta: NodeMetaDTO]
}>()

const registry = useNodeRegistryStore()
const { sortedNodes, loading, error } = storeToRefs(registry)
</script>

<template>
  <aside class="node-palette">
    <div class="node-palette__title">节点库</div>
    <div v-if="loading" class="node-palette__state">加载中...</div>
    <div v-else-if="error" class="node-palette__state node-palette__state--error">{{ error }}</div>
    <button
      v-for="meta in sortedNodes"
      :key="meta.nodeType"
      class="node-palette__item"
      @click="emit('add', meta)"
    >
      <strong>{{ meta.displayName }}</strong>
      <span>{{ meta.nodeType }}</span>
    </button>
  </aside>
</template>

<style scoped>
.node-palette {
  width: 280px;
  padding: 16px;
  border-right: 1px solid #1e293b;
  background: #020617;
  display: grid;
  gap: 12px;
  overflow: auto;
}
.node-palette__title {
  font-size: 18px;
  font-weight: 700;
}
.node-palette__item {
  border: 1px solid #334155;
  border-radius: 14px;
  background: #111827;
  color: inherit;
  padding: 12px;
  text-align: left;
  display: grid;
  gap: 6px;
  cursor: pointer;
}
.node-palette__item span {
  font-size: 12px;
  color: #94a3b8;
}
.node-palette__state {
  font-size: 13px;
  color: #94a3b8;
}
.node-palette__state--error {
  color: #fca5a5;
}
</style>
