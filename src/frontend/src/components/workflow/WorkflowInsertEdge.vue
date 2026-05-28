<script setup lang="ts">
import { computed, ref } from 'vue'
import { BaseEdge, EdgeLabelRenderer, getBezierPath, useVueFlow } from '@vue-flow/core'
import type { EdgeProps } from '@vue-flow/core'

const props = defineProps<EdgeProps>()

const emit = defineEmits<{
  insert: [payload: { edgeId: string; anchor: { x: number; y: number } }]
}>()

const { removeEdges } = useVueFlow()
const isHovered = ref(false)

const pathData = computed(() => getBezierPath({
  sourceX: props.sourceX,
  sourceY: props.sourceY,
  sourcePosition: props.sourcePosition,
  targetX: props.targetX,
  targetY: props.targetY,
  targetPosition: props.targetPosition,
}))

function handleInsert() {
  const [, labelX, labelY] = pathData.value
  emit('insert', { edgeId: props.id, anchor: { x: labelX, y: labelY } })
}

function handleDelete(event: MouseEvent) {
  event.stopPropagation()
  removeEdges([props.id])
}
</script>

<template>
  <BaseEdge
    :id="id"
    :path="pathData[0]"
    :style="style"
    :marker-end="markerEnd"
    @mouseenter="isHovered = true"
    @mouseleave="isHovered = false"
  />
  <EdgeLabelRenderer>
    <div
      class="workflow-insert-edge__buttons"
      :style="{ transform: `translate(-50%, -50%) translate(${pathData[1]}px, ${pathData[2]}px)` }"
    >
      <button
        v-if="isHovered"
        class="workflow-insert-edge__button workflow-insert-edge__button--delete"
        @click.stop="handleDelete"
        title="删除连接"
      >
        ×
      </button>
      <button
        class="workflow-insert-edge__button workflow-insert-edge__button--insert"
        @click.stop="handleInsert"
        title="插入节点"
      >
        +
      </button>
    </div>
  </EdgeLabelRenderer>
</template>

<style scoped>
.workflow-insert-edge__buttons {
  position: absolute;
  display: flex;
  gap: 4px;
  pointer-events: all;
}

.workflow-insert-edge__button {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--iap-input-border-focus);
  border-radius: 999px;
  background: var(--iap-panel-bg);
  color: var(--iap-text-accent);
  box-shadow: var(--iap-shadow-panel);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  transition: all 0.15s ease;
}

.workflow-insert-edge__button:hover {
  transform: scale(1.1);
  box-shadow: var(--iap-shadow-panel), 0 0 0 2px var(--iap-input-border-focus);
}

.workflow-insert-edge__button--delete {
  color: var(--iap-text-danger, #ef4444);
  border-color: var(--iap-text-danger, #ef4444);
  font-size: 20px;
  font-weight: 300;
}

.workflow-insert-edge__button--delete:hover {
  background: var(--iap-text-danger, #ef4444);
  color: white;
}

.workflow-insert-edge__button--insert {
  font-size: 18px;
  font-weight: 400;
}
</style>
