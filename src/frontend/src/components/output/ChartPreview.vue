<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { QueryResultDTO, StandardResultDTO, ChartRecommendationDTO } from '@/types/contract'
import { resolveRendererModel, type RendererMode } from '@/components/output/renderer'
import AiRecommendBadge from '@/components/ai/AiRecommendBadge.vue'
import { recommendChart } from '@/api/ai'

const props = defineProps<{
  result?: StandardResultDTO | QueryResultDTO
  mode?: RendererMode
}>()

const emit = defineEmits<{
  'chartTypeChange': [chartType: string]
}>()

const model = computed(() => resolveRendererModel(props.result, props.mode ?? 'runtime'))
const fallback = computed(() => model.value.fallback ?? { title: '暂无图表数据', description: '当前结果暂不可展示。' })
const maxValue = computed(() => {
  if (model.value.kind !== 'chart') {
    return 0
  }
  return model.value.series.flatMap(item => item.data).reduce<number>((max, value) => {
    const next = Number(value)
    return Number.isFinite(next) ? Math.max(max, next) : max
  }, 0)
})

function asPercent(value: unknown) {
  const current = Number(value)
  if (!Number.isFinite(current) || maxValue.value <= 0) {
    return '0%'
  }
  return `${Math.max((current / maxValue.value) * 100, 6)}%`
}

// AI 图表推荐
const recommendation = ref<ChartRecommendationDTO | null>(null)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let currentAbortController: AbortController | null = null

function extractFields() {
  const result = props.result as StandardResultDTO | undefined
  return result?.dataset?.schema?.fields ?? []
}

watch(() => props.result, (newResult) => {
  // 取消上一次未完成的请求和定时器
  if (debounceTimer !== null) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  if (currentAbortController) {
    currentAbortController.abort()
    currentAbortController = null
  }
  if (!newResult) { recommendation.value = null; return }
  const fields = extractFields()
  if (fields.length === 0) { recommendation.value = null; return }

  debounceTimer = setTimeout(async () => {
    const controller = new AbortController()
    currentAbortController = controller
    try {
      const recs = await recommendChart({ fields }, controller.signal)
      recommendation.value = recs[0] ?? null
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') return
      if ((err as { code?: string }).code === 'ERR_CANCELED') return
      recommendation.value = null
    } finally {
      if (currentAbortController === controller) currentAbortController = null
    }
  }, 200)
}, { immediate: true })

function applyRecommendation() {
  if (recommendation.value) {
    emit('chartTypeChange', recommendation.value.chartType)
  }
}
</script>

<template>
  <div class="chart-preview">
    <div class="chart-preview__header">
      <strong>{{ model.title }}</strong>
      <span>{{ model.kind === 'chart' ? model.chartType : 'EMPTY' }}</span>
    </div>
    <AiRecommendBadge
      v-if="recommendation"
      :recommendation="recommendation"
      @accept="applyRecommendation"
    />
    <div v-for="notice in model.notices" :key="notice.message" class="chart-preview__notice" :data-tone="notice.tone">
      {{ notice.message }}
    </div>
    <template v-if="model.kind === 'chart' && !model.empty">
      <div class="chart-preview__canvas">
        <div class="chart-preview__axis" v-if="model.categories.length">
          <span v-for="category in model.categories" :key="category">{{ category }}</span>
        </div>
        <div v-else class="chart-preview__axis chart-preview__axis--placeholder">
          <span>当前图表未提供类目轴，已按序列数据渲染。</span>
        </div>
        <div class="chart-preview__series-list">
          <div v-for="series in model.series" :key="series.name" class="chart-preview__series">
            <div class="chart-preview__series-title">{{ series.name }}</div>
            <div class="chart-preview__bars">
              <div v-for="(item, index) in series.data" :key="`${series.name}-${index}`" class="chart-preview__bar-item">
                <div
                  class="chart-preview__bar"
                  :data-chart-type="model.chartType"
                  :style="{ height: asPercent(item) }"
                />
                <strong>{{ model.categories[index] ?? `#${index + 1}` }}</strong>
                <span>{{ item ?? '-' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="chart-preview__legend" v-if="model.showLegend">
        <span v-for="series in model.series" :key="series.name">{{ series.name }}</span>
      </div>
    </template>
    <div v-else class="chart-preview__empty">
      <strong>{{ fallback.title }}</strong>
      <span>{{ fallback.description }}</span>
    </div>
  </div>
</template>

<style scoped>
.chart-preview {
  display: grid;
  gap: 10px;
  border: 1px solid #1e293b;
  border-radius: 16px;
  background: #0b1120;
  padding: 14px;
}
.chart-preview__header,
.chart-preview__legend {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #94a3b8;
  font-size: 12px;
  flex-wrap: wrap;
}
.chart-preview__header strong {
  color: #e2e8f0;
  font-size: 14px;
}
.chart-preview__notice {
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 12px;
  color: #cbd5e1;
  background: rgba(59, 130, 246, 0.12);
}
.chart-preview__notice[data-tone='warning'] {
  background: rgba(245, 158, 11, 0.14);
}
.chart-preview__canvas {
  display: grid;
  gap: 12px;
}
.chart-preview__axis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(60px, 1fr));
  gap: 8px;
  color: #94a3b8;
  font-size: 12px;
}
.chart-preview__axis--placeholder {
  grid-template-columns: 1fr;
}
.chart-preview__series-list {
  display: grid;
  gap: 12px;
}
.chart-preview__series {
  display: grid;
  gap: 8px;
}
.chart-preview__series-title {
  color: #cbd5e1;
  font-size: 12px;
}
.chart-preview__bars {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(48px, 1fr));
  gap: 8px;
  align-items: end;
  min-height: 160px;
}
.chart-preview__bar-item {
  display: grid;
  gap: 6px;
  align-items: end;
  justify-items: center;
  font-size: 12px;
  color: #94a3b8;
}
.chart-preview__bar-item strong {
  color: #cbd5e1;
  font-size: 11px;
}
.chart-preview__bar {
  width: 100%;
  min-height: 8px;
  border-radius: 10px 10px 4px 4px;
  background: linear-gradient(180deg, #38bdf8, #2563eb);
}
.chart-preview__bar[data-chart-type='LINE'],
.chart-preview__bar[data-chart-type='AREA'] {
  background: linear-gradient(180deg, #22c55e, #2563eb);
}
.chart-preview__bar[data-chart-type='PIE'] {
  border-radius: 999px;
  background: linear-gradient(180deg, #f59e0b, #ef4444);
}
.chart-preview__bar[data-chart-type='SCATTER'] {
  width: 14px;
  border-radius: 999px;
  background: #38bdf8;
}
.chart-preview__empty {
  display: grid;
  gap: 4px;
  padding: 18px;
  border: 1px dashed #334155;
  border-radius: 12px;
  color: #94a3b8;
  text-align: center;
}
.chart-preview__empty strong {
  color: #e2e8f0;
}
</style>
