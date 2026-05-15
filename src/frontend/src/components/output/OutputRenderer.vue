<script setup lang="ts">
import { computed } from 'vue'
import type { QueryResultDTO, StandardResultDTO } from '@/types/contract'
import { resolveRendererModel, type RendererMode } from '@/components/output/renderer'
import ChartPreview from './ChartPreview.vue'
import TablePreview from './TablePreview.vue'

const props = defineProps<{
  result?: StandardResultDTO | QueryResultDTO
  mode?: RendererMode
}>()

const model = computed(() => resolveRendererModel(props.result, props.mode ?? 'runtime'))
</script>

<template>
  <ChartPreview v-if="model.kind === 'chart'" :result="result" :mode="mode" />
  <TablePreview v-else-if="model.kind === 'table'" :result="result" :mode="mode" />
  <div v-else class="out-empty">
    <div class="out-empty__icon">▭</div>
    <div class="out-empty__title">{{ model.fallback?.title ?? '暂无可渲染结果' }}</div>
    <div class="out-empty__desc">{{ model.fallback?.description }}</div>
  </div>
</template>

<style scoped>
.out-empty {
  display: grid;
  gap: 6px;
  padding: 24px 16px;
  border: 1px dashed #1e293b;
  border-radius: 12px;
  color: #475569;
  text-align: center;
}
.out-empty__icon {
  font-size: 22px;
  opacity: 0.4;
}
.out-empty__title {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}
.out-empty__desc {
  font-size: 11px;
  color: #334155;
  line-height: 1.5;
}
</style>
