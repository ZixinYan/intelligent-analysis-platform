<script setup lang="ts">
import type { ChartRecommendationDTO } from '@/types/contract'

const props = defineProps<{
  recommendation: ChartRecommendationDTO
}>()

const emit = defineEmits<{
  accept: []
}>()

function confidencePercent(confidence: number) {
  return Math.round(confidence * 100)
}
</script>

<template>
  <div class="ai-recommend-badge" :title="recommendation.reason">
    <span class="ai-recommend-badge__icon">✦</span>
    <span class="ai-recommend-badge__type">AI 推荐：{{ recommendation.chartType }}</span>
    <span class="ai-recommend-badge__confidence">{{ confidencePercent(recommendation.confidence) }}%</span>
    <button class="ai-recommend-badge__apply" @click.stop="emit('accept')">应用</button>
  </div>
  <div class="ai-recommend-badge__reason">{{ recommendation.reason }}</div>
</template>

<style scoped>
.ai-recommend-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.35);
  border-radius: 8px;
  font-size: 12px;
  color: #a5b4fc;
  cursor: default;
}
.ai-recommend-badge__icon { font-size: 11px; }
.ai-recommend-badge__type { font-weight: 600; }
.ai-recommend-badge__confidence { color: #6366f1; }
.ai-recommend-badge__apply {
  background: rgba(99, 102, 241, 0.3);
  border: 1px solid rgba(99, 102, 241, 0.5);
  border-radius: 6px;
  color: #c7d2fe;
  font-size: 11px;
  padding: 2px 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.ai-recommend-badge__apply:hover { background: rgba(99, 102, 241, 0.5); }
.ai-recommend-badge__reason {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
