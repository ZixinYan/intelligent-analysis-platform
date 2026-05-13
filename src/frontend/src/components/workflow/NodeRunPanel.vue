<script setup lang="ts">
import { computed } from 'vue'
import TablePreview from '@/components/output/TablePreview.vue'
import ChartPreview from '@/components/output/ChartPreview.vue'
import type { NodeResultDTO } from '@/types/contract'
import type { StreamNodeState } from '@/composables/useWorkflowStream'

const props = defineProps<{
  /** 同步/批量执行时的最终结果 */
  result?: NodeResultDTO
  loading?: boolean
  /** 流式执行时的当前节点状态（useWorkflowStream 提供） */
  streamState?: StreamNodeState
}>()

// ---- 同步执行路径 ----
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

// ---- 流式执行路径 ----
const isStreamRunning = computed(() => props.streamState?.status === 'running')
const isStreamSuccess = computed(() => props.streamState?.status === 'success')
const isStreamError = computed(() => props.streamState?.status === 'error')
const streamHasChunks = computed(() => (props.streamState?.chunks?.length ?? 0) > 0)

const streamReceivedRows = computed(() => {
  if (!props.streamState?.chunks?.length) return 0
  return props.streamState.chunks.reduce((sum, c) => sum + (c?.length ?? 0), 0)
})

const streamElapsedLabel = computed(() => {
  const ms = props.streamState?.elapsedMs
  if (ms === undefined) return undefined
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s`
})

/** 将分块数据扁平化用于预览（最多展示前 200 行） */
const flatPreviewRows = computed<Record<string, unknown>[]>(() => {
  if (!props.streamState?.chunks?.length) return []
  return props.streamState.chunks.flat().slice(0, 200) as Record<string, unknown>[]
})

const streamingMode = computed(() => !!props.streamState)
</script>

<template>
  <div class="nrp">
    <!-- ===== 流式模式 ===== -->
    <template v-if="streamingMode">
      <!-- 执行中：进度指示 -->
      <div v-if="isStreamRunning" class="nrp__stream-running">
        <span class="nrp__spinner" />
        <span class="nrp__stream-label">节点执行中…</span>
        <span v-if="streamHasChunks" class="nrp__stream-progress">
          已接收 {{ streamReceivedRows }} 行
        </span>
      </div>

      <!-- 大数据集实时预览（接收到 node_progress 分块时显示） -->
      <div v-if="streamHasChunks" class="nrp__stream-preview">
        <div class="nrp__section-label">
          数据流预览（前 200 行，共已收 {{ streamReceivedRows }} 行）
        </div>
        <div class="nrp__preview-table-wrap">
          <table v-if="flatPreviewRows.length" class="nrp__preview-table">
            <thead>
              <tr>
                <th v-for="col in Object.keys(flatPreviewRows[0] ?? {})" :key="col">{{ col }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in flatPreviewRows" :key="i">
                <td v-for="col in Object.keys(row)" :key="col">{{ row[col] }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 流式完成后的状态栏 -->
      <div
        v-if="isStreamSuccess || isStreamError"
        class="nrp__status-bar"
        :class="isStreamSuccess ? 'nrp__status-bar--ok' : 'nrp__status-bar--err'"
      >
        <span class="nrp__status-dot" />
        <span class="nrp__status-label">{{ isStreamSuccess ? '执行成功' : '执行失败' }}</span>
        <span v-if="streamElapsedLabel" class="nrp__elapsed">{{ streamElapsedLabel }}</span>
      </div>

      <!-- 流式完成后的最终结果（非大数据集） -->
      <template v-if="isStreamSuccess && streamState?.result">
        <div
          v-if="(streamState.result.kind === 'DATASET' || streamState.result.kind === 'TABLE') && !streamHasChunks"
          class="nrp__result-block"
        >
          <TablePreview :result="streamState.result" mode="preview" />
        </div>
        <div v-if="streamState.result.kind === 'CHART'" class="nrp__result-block">
          <ChartPreview :result="streamState.result" mode="preview" />
        </div>
      </template>

      <!-- 流式错误 -->
      <div v-if="isStreamError && streamState?.error" class="nrp__error">
        <div class="nrp__error-code">{{ streamState.error.code ?? 'ERROR' }}</div>
        <div class="nrp__error-msg">{{ streamState.error.message ?? '节点执行失败' }}</div>
      </div>
    </template>

    <!-- ===== 同步模式（原有逻辑不变） ===== -->
    <template v-else>
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

/* Streaming mode */
.nrp__stream-running {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #60a5fa;
  font-size: 13px;
  padding: 12px 0;
}
.nrp__stream-label {
  font-weight: 500;
}
.nrp__stream-progress {
  margin-left: auto;
  font-size: 11px;
  color: #94a3b8;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}

/* Streaming data preview */
.nrp__stream-preview {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.nrp__preview-table-wrap {
  overflow: auto;
  max-height: 280px;
  border: 1px solid #1e293b;
  border-radius: 8px;
}
.nrp__preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  color: #94a3b8;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}
.nrp__preview-table th {
  position: sticky;
  top: 0;
  background: #0f172a;
  padding: 6px 10px;
  text-align: left;
  font-weight: 700;
  font-size: 10px;
  letter-spacing: 0.06em;
  color: #64748b;
  border-bottom: 1px solid #1e293b;
  white-space: nowrap;
}
.nrp__preview-table td {
  padding: 4px 10px;
  border-bottom: 1px solid rgba(30, 41, 59, 0.6);
  white-space: nowrap;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.nrp__preview-table tbody tr:hover td {
  background: rgba(30, 41, 59, 0.4);
}
</style>
