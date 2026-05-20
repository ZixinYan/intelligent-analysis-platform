<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import type { NodeMetaDTO } from '@/types/contract'
import { useNodeRegistryStore } from '@/stores/node-registry'
import { resolveNodeIcon } from '@/utils/node-preview'
import type { InsertAnchor, WorkflowInsertTrigger } from './insert-types'
import { groupNodeCatalog, shortNodeDesc } from './node-catalog'

const props = defineProps<{
  visible: boolean
  anchor: InsertAnchor
  trigger?: WorkflowInsertTrigger | null
}>()

const emit = defineEmits<{
  select: [meta: NodeMetaDTO]
  close: []
}>()

const registry = useNodeRegistryStore()
const { sortedNodes, loading, error } = storeToRefs(registry)
const keyword = ref('')

const groupedNodes = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  const filtered = normalizedKeyword
    ? sortedNodes.value.filter(node => {
      const displayName = node.displayName?.toLowerCase() ?? ''
      const nodeType = node.nodeType?.toLowerCase() ?? ''
      const description = node.description?.toLowerCase() ?? ''
      return displayName.includes(normalizedKeyword)
        || nodeType.includes(normalizedKeyword)
        || description.includes(normalizedKeyword)
    })
    : sortedNodes.value
  return groupNodeCatalog(filtered)
})

const pickerStyle = computed(() => ({
  left: `${Math.max(16, props.anchor.x)}px`,
  top: `${Math.max(16, props.anchor.y)}px`,
}))

watch(() => props.visible, (visible) => {
  if (!visible) {
    keyword.value = ''
  }
})
</script>

<template>
  <teleport to="body">
    <div v-if="visible" class="picker-overlay" @click="emit('close')">
      <div class="picker" :style="pickerStyle" @click.stop>
        <div class="picker__header">
          <div>
            <div class="picker__title">选择节点</div>
            <div class="picker__subtitle">点击后立即插入到当前加号位置</div>
          </div>
          <button class="picker__close" @click="emit('close')">×</button>
        </div>
        <input v-model="keyword" class="picker__search" placeholder="搜索节点类型" />
        <div v-if="loading" class="picker__state">加载中…</div>
        <div v-else-if="error" class="picker__state picker__state--error">{{ error }}</div>
        <div v-else class="picker__body">
          <div v-for="group in groupedNodes" :key="group.category" class="picker__group">
            <div class="picker__group-title" :style="{ '--cat': group.meta.color }">
              <span class="picker__group-dot" />
              <span>{{ group.meta.label }}</span>
              <span class="picker__group-desc">{{ group.meta.desc }}</span>
            </div>
            <button
              v-for="meta in group.nodes"
              :key="meta.nodeType"
              class="picker__item"
              :style="{ '--cat': group.meta.color }"
              @click="emit('select', meta)"
            >
              <span class="picker__item-icon">{{ resolveNodeIcon(meta) }}</span>
              <span class="picker__item-main">
                <span class="picker__item-name">{{ meta.displayName }}</span>
                <span class="picker__item-desc">{{ shortNodeDesc(meta.description) || meta.nodeType }}</span>
              </span>
            </button>
          </div>
          <div v-if="!groupedNodes.length" class="picker__state">暂无匹配节点</div>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.picker-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
}
.picker {
  position: fixed;
  width: 320px;
  max-height: min(560px, calc(100vh - 32px));
  display: flex;
  flex-direction: column;
  border: 1px solid var(--iap-divider);
  border-radius: 16px;
  background: var(--iap-panel-bg);
  box-shadow: var(--iap-shadow-panel);
  overflow: hidden;
}
.picker__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px 10px;
}
.picker__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--iap-text-primary);
}
.picker__subtitle {
  margin-top: 4px;
  font-size: 11px;
  color: var(--iap-text-tertiary);
}
.picker__close {
  border: none;
  background: transparent;
  color: var(--iap-text-secondary);
  font-size: 20px;
  cursor: pointer;
}
.picker__search {
  margin: 0 16px 12px;
  border: 1px solid var(--iap-input-border);
  border-radius: 10px;
  background: var(--iap-input-bg);
  color: var(--iap-text-primary);
  padding: 10px 12px;
  outline: none;
}
.picker__body {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px 12px;
}
.picker__group {
  margin-bottom: 8px;
}
.picker__group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 8px 6px;
  color: var(--cat);
  font-size: 11px;
  font-weight: 700;
}
.picker__group-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--cat);
}
.picker__group-desc {
  color: var(--iap-text-tertiary);
  font-weight: 400;
}
.picker__item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--iap-text-primary);
  padding: 10px;
  text-align: left;
  cursor: pointer;
}
.picker__item:hover {
  border-color: color-mix(in srgb, var(--cat) 24%, var(--iap-divider));
  background: color-mix(in srgb, var(--cat) 8%, var(--iap-surface-hover));
}
.picker__item-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--cat) 10%, var(--iap-surface-secondary));
}
.picker__item-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.picker__item-name {
  font-size: 12px;
  font-weight: 600;
}
.picker__item-desc {
  margin-top: 2px;
  font-size: 11px;
  color: var(--iap-text-tertiary);
}
.picker__state {
  padding: 24px 16px;
  text-align: center;
  font-size: 12px;
  color: var(--iap-text-tertiary);
}
.picker__state--error {
  color: var(--iap-error-text);
}
</style>
