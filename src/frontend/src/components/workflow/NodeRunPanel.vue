<script setup lang="ts">
import { computed } from 'vue'
import TablePreview from '@/components/output/TablePreview.vue'
import ChartPreview from '@/components/output/ChartPreview.vue'
import type { NodeResultDTO } from '@/types/contract'

const props = defineProps<{
  result?: NodeResultDTO
  loading?: boolean
}>()

const hasResult = computed(() => !!props.result)
const isSucceeded = computed(() => props.result?.status === 'SUCCEEDED')
const isFailed = computed(() => props.result?.status === 'FAILED')
const resultKind = computed(() => props.result?.result?.kind)
const standardResult = computed(() => props.result?.result)

const elapsedLabel = computed(() => {
  const ms = props.result?.meta?.elapsedMs
  if (ms === undefined) return undefined
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s`
})
</script>

<template>
  <div class="nrp">
    <!-- Loading -->
    <div v-if="loading" class="nrp__loading">
      <span class="nrp__spinner" />
      <span>节点执行中…</span>
    </div>

    <!-- Empty -->
    <div v-else-if="!hasResult" class="nrp__empty">
      <div class="nrp__empty-icon">▷</div>
      <div class="nrp__empty-text">点击节点上的运行按钮或顶部「运行」执行此节点</div>
    </div>

    <template v-else>
      <!-- Status bar -->
      <div class="nrp__status-bar" :class="isSucceeded ? 'nrp__status-bar--ok' : 'nrp__status-bar--err'">
        <span class="nrp__status-dot" />
        <span class="nrp__status-label">{{ isSucceeded ? '执行成功' : '执行失败' }}</span>
        <span v-if="elapsedLabel" class="nrp__elapsed">{{ elapsedLabel }}</span>
      </div>

      <!-- Error -->
      <div v-if="isFailed && result?.error" class="nrp__error">
        <div class="nrp__error-code">{{ result.error.code ?? 'ERROR' }}</div>
        <div class="nrp__error-msg">{{ result.error.message ?? '未知错误' }}</div>
      </div>

      <!-- DATASET result -->
      <div v-if="(resultKind === 'DATASET' || resultKind === 'TABLE') && standardResult" class="nrp__result-block">
        <TablePreview :result="standardResult" mode="preview" />
      </div>

      <!-- CHART result -->
      <div v-if="resultKind === 'CHART' && standardResult" class="nrp__result-block">
        <ChartPreview :result="standardResult" mode="preview" />
      </div>

      <!-- VARIABLES result -->
      <div v-if="resultKind === 'VARIABLES' && standardResult?.variables" class="nrp__result-block">
        <div class="nrp__section-label">输出变量</div>
        <pre class="nrp__json">{{ JSON.stringify(standardResult.variables, null, 2) }}</pre>
      </div>
    </template>
  </div>
</template>

<style scoped>
.nrp {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Loading */
.nrp__loading {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #64748b;
  font-size: 13px;
  padding: 16px 0;
}
.nrp__spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid #1e293b;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Empty */
.nrp__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px 16px;
  color: #334155;
}
.nrp__empty-icon {
  font-size: 28px;
  opacity: 0.5;
}
.nrp__empty-text {
  font-size: 12px;
  text-align: center;
  line-height: 1.6;
}

/* Status bar */
.nrp__status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
}
.nrp__status-bar--ok {
  background: rgba(34, 197, 94, 0.08);
  border: 1px solid rgba(34, 197, 94, 0.2);
  color: #22c55e;
}
.nrp__status-bar--err {
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #ef4444;
}
.nrp__status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}
.nrp__status-label {
  font-weight: 600;
}
.nrp__elapsed {
  margin-left: auto;
  font-size: 11px;
  opacity: 0.7;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}

/* Error block */
.nrp__error {
  padding: 10px 12px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.15);
  border-radius: 8px;
}
.nrp__error-code {
  font-size: 11px;
  font-weight: 700;
  color: #ef4444;
  font-family: ui-monospace, monospace;
  margin-bottom: 4px;
}
.nrp__error-msg {
  font-size: 12px;
  color: #fca5a5;
  line-height: 1.5;
}

/* Result block */
.nrp__result-block {
  border-radius: 8px;
  overflow: hidden;
}
.nrp__section-label {
  font-size: 10px;
  font-weight: 700;
  color: #475569;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 6px;
}
.nrp__json {
  margin: 0;
  padding: 10px 12px;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid #1e293b;
  border-radius: 8px;
  font-size: 11px;
  color: #94a3b8;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow-y: auto;
}
</style>
