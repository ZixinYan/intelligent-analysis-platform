<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useNodeRegistryStore } from '@/stores/node-registry'
import { resolveNodeIcon } from '@/utils/node-preview'
import type { NodeMetaDTO } from '@/types/contract'

const emit = defineEmits<{
  add: [meta: NodeMetaDTO]
}>()

const registry = useNodeRegistryStore()
const { sortedNodes, loading, error } = storeToRefs(registry)

const categoryMeta: Record<string, { label: string; color: string; desc: string }> = {
  QUERY:      { label: '取数',  color: '#3b82f6', desc: '连接数据源，执行查询' },
  COMPUTE:    { label: '计算',  color: '#8b5cf6', desc: '数据转换与运算' },
  OUTPUT:     { label: '输出',  color: '#10b981', desc: '渲染图表或表格' },
  GOVERNANCE: { label: '治理',  color: '#f59e0b', desc: '数据质量与权限' },
  ANALYSIS:   { label: '分析',  color: '#06b6d4', desc: '数据分析智能节点' },
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
    meta: categoryMeta[category] ?? { label: category, color: '#64748b', desc: '' },
    nodes,
  }))
})

function shortDesc(desc?: string): string {
  if (!desc) return ''
  const firstClause = desc.split(/[；，、]/)[0]
  return firstClause.length > 22 ? firstClause.slice(0, 22) + '…' : firstClause
}
</script>

<template>
  <aside class="palette">
    <div class="palette__header">
      <div class="palette__title">节点库</div>
      <div class="palette__subtitle">拖拽或点击添加节点</div>
    </div>

    <div v-if="loading" class="palette__state">
      <span class="palette__spinner" />加载中…
    </div>
    <div v-else-if="error" class="palette__state palette__state--error">{{ error }}</div>

    <template v-else>
      <div v-for="group in groupedNodes" :key="group.category" class="palette__group">
        <div class="palette__category" :style="{ '--cat': group.meta.color }">
          <span class="palette__category-dot" />
          <span class="palette__category-label">{{ group.meta.label }}</span>
          <span class="palette__category-desc">{{ group.meta.desc }}</span>
        </div>

        <button
          v-for="meta in group.nodes"
          :key="meta.nodeType"
          class="palette__item"
          :style="{ '--cat': group.meta.color }"
          :title="meta.description ?? meta.displayName"
          @click="emit('add', meta)"
        >
          <div class="palette__item-icon">{{ resolveNodeIcon(meta) }}</div>
          <div class="palette__item-body">
            <div class="palette__item-name">{{ meta.displayName }}</div>
            <div v-if="meta.description" class="palette__item-desc">{{ shortDesc(meta.description) }}</div>
          </div>
          <div class="palette__item-arrow">+</div>
        </button>
      </div>
    </template>
  </aside>
</template>

<style scoped>
.palette {
  padding: 14px 10px;
  border-right: 1px solid var(--iap-divider);
  background: var(--iap-sidebar-bg);
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.palette__header {
  padding: 2px 6px 12px;
  border-bottom: 1px solid var(--iap-divider);
  margin-bottom: 4px;
}

.palette__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--iap-text-primary);
  letter-spacing: 0.03em;
}

.palette__subtitle {
  font-size: 11px;
  color: var(--iap-text-tertiary);
  margin-top: 3px;
}

.palette__group {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-bottom: 6px;
}

.palette__category {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 6px 4px;
}

.palette__category-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--cat, #64748b);
  flex-shrink: 0;
}

.palette__category-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--cat, #64748b);
  text-transform: uppercase;
  letter-spacing: 0.07em;
}

.palette__category-desc {
  font-size: 10px;
  color: var(--iap-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.palette__item {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--iap-text-secondary);
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
  width: 100%;
  transition: background 0.12s, border-color 0.12s;
}

.palette__item:hover {
  background: color-mix(in srgb, var(--cat) 6%, var(--iap-surface-hover));
  border-color: color-mix(in srgb, var(--cat) 24%, var(--iap-divider));
}

.palette__item:hover .palette__item-arrow {
  opacity: 1;
  color: var(--cat);
}

.palette__item-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--cat) 10%, var(--iap-surface-secondary));
  border: 1px solid color-mix(in srgb, var(--cat) 20%, var(--iap-divider));
  display: grid;
  place-items: center;
  font-size: 15px;
  line-height: 1;
}

.palette__item-body {
  flex: 1;
  min-width: 0;
}

.palette__item-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--iap-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: 0.01em;
}

.palette__item-desc {
  font-size: 10.5px;
  color: var(--iap-text-tertiary);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.palette__item:hover .palette__item-desc {
  color: var(--iap-text-secondary);
}

.palette__item-arrow {
  font-size: 16px;
  font-weight: 300;
  color: var(--iap-text-placeholder);
  opacity: 0;
  flex-shrink: 0;
  transition: opacity 0.12s, color 0.12s;
  line-height: 1;
}

.palette__state {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--iap-text-tertiary);
  padding: 12px 8px;
}

.palette__state--error {
  color: var(--iap-error-text);
}

.palette__spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid var(--iap-divider-strong);
  border-top-color: var(--iap-text-accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
