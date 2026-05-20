<script setup lang="ts">
import { computed } from 'vue'
import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@vue-flow/core'
import type { EdgeProps } from '@vue-flow/core'

const props = defineProps<EdgeProps>()

const emit = defineEmits<{
  insert: [payload: { edgeId: string; anchor: { x: number; y: number } }]
}>()

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
</script>

<template>
  <BaseEdge :id="id" :path="pathData[0]" :style="style" :marker-end="markerEnd" />
  <EdgeLabelRenderer>
    <button
      class="workflow-insert-edge__button"
      :style="{ transform: `translate(-50%, -50%) translate(${pathData[1]}px, ${pathData[2]}px)` }"
      @click.stop="handleInsert"
    >
      +
    </button>
  </EdgeLabelRenderer>
</template>

<style scoped>
.workflow-insert-edge__button {
  position: absolute;
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
}
</style>
