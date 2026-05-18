<template>
  <div class="run-history-panel">
    <div class="panel-header">
      <span class="panel-title">执行历史</span>
      <button class="btn-refresh" @click="load">刷新</button>
    </div>

    <div class="filter-bar">
      <select v-model="filterStatus" @change="onFilterChange">
        <option value="">全部状态</option>
        <option value="SUCCEEDED">成功</option>
        <option value="FAILED">失败</option>
        <option value="RUNNING">运行中</option>
      </select>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <ul v-else class="run-list">
      <li
        v-for="run in runs"
        :key="run.runId"
        class="run-item"
        :class="{ selected: selectedRunId === run.runId }"
        @click="selectRun(run.runId)"
      >
        <div class="run-info">
          <span class="run-status" :class="statusClass(run.status)">{{ statusLabel(run.status) }}</span>
          <span class="run-time">{{ formatTime(run.startedAt) }}</span>
        </div>
        <div class="run-meta">
          <span v-if="run.elapsedMs != null">耗时 {{ formatElapsed(run.elapsedMs) }}</span>
          <span v-if="run.nodeCount != null">{{ run.nodeCount }} 个节点</span>
        </div>
      </li>
    </ul>

    <div class="pagination">
      <button :disabled="page <= 1" @click="prevPage">上一页</button>
      <span>第 {{ page }} 页 / 共 {{ totalPages }} 页</span>
      <button :disabled="page >= totalPages" @click="nextPage">下一页</button>
    </div>

    <div v-if="selectedRun" class="trace-viewer">
      <div class="trace-header">
        <span>节点执行详情</span>
        <button class="btn-close" @click="selectedRunId = null">✕</button>
      </div>
      <div v-if="traceLoading" class="loading">加载中...</div>
      <ul v-else class="trace-list">
        <li
          v-for="trace in selectedRun.nodeTraces"
          :key="trace.nodeId"
          class="trace-item"
          :class="nodeStatusClass(trace.status)"
        >
          <div class="trace-row">
            <span class="trace-node-id">{{ trace.nodeId }}</span>
            <span class="trace-node-type">{{ trace.nodeType }}</span>
            <span class="trace-status-badge" :class="nodeStatusClass(trace.status)">
              {{ trace.status }}
            </span>
          </div>
          <div class="trace-meta">
            <span v-if="trace.elapsedMs != null">{{ trace.elapsedMs }} ms</span>
            <span v-if="trace.rowCount != null">{{ trace.rowCount }} 行</span>
            <span v-if="trace.cached" class="badge-cached">缓存命中</span>
            <span v-if="trace.pushdown" class="badge-pushdown">下推</span>
          </div>
          <div v-if="trace.error" class="trace-error">{{ trace.error }}</div>
        </li>
        <li v-if="!selectedRun.nodeTraces || selectedRun.nodeTraces.length === 0" class="trace-empty">
          无节点记录
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listRuns, getRunDetail } from '@/api/workflow'
import type { WorkflowRunLogDTO } from '@/types/contract'

const props = defineProps<{ workflowId: string }>()

const runs = ref<WorkflowRunLogDTO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const traceLoading = ref(false)
const error = ref<string | null>(null)
const filterStatus = ref('')
const selectedRunId = ref<string | null>(null)
const selectedRun = ref<WorkflowRunLogDTO | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await listRuns(
      props.workflowId,
      page.value,
      pageSize,
      filterStatus.value || undefined,
    )
    runs.value = result.items
    total.value = result.total
  }
  catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
  finally {
    loading.value = false
  }
}

async function selectRun(runId: string) {
  if (selectedRunId.value === runId) {
    selectedRunId.value = null
    selectedRun.value = null
    return
  }
  selectedRunId.value = runId
  traceLoading.value = true
  try {
    selectedRun.value = await getRunDetail(props.workflowId, runId)
  }
  catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载详情失败'
  }
  finally {
    traceLoading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  load()
}

function prevPage() {
  if (page.value > 1) { page.value--; load() }
}

function nextPage() {
  if (page.value < totalPages.value) { page.value++; load() }
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleString()
}

function formatElapsed(ms: number) {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    RUNNING: '运行中', SUCCEEDED: '成功', FAILED: '失败', CANCELLED: '已取消',
  }
  return map[status] ?? status
}

function statusClass(status: string) {
  return {
    'status-running': status === 'RUNNING',
    'status-success': status === 'SUCCEEDED',
    'status-failed': status === 'FAILED',
    'status-cancelled': status === 'CANCELLED',
  }
}

function nodeStatusClass(status: string) {
  return {
    'node-success': status === 'SUCCEEDED',
    'node-failed': status === 'FAILED',
    'node-skipped': status === 'SKIPPED',
    'node-running': status === 'RUNNING',
  }
}

onMounted(load)
</script>

<style scoped>
.run-history-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--iap-panel-bg);
  border-left: 1px solid var(--iap-divider);
  min-width: 280px;
  max-height: 100%;
  overflow-y: auto;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--iap-text-primary);
}
.btn-refresh {
  padding: 6px 12px;
  background: var(--iap-btn-primary-bg);
  color: var(--iap-btn-primary-text);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
}
.filter-bar select {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--iap-input-border);
  border-radius: 8px;
  font-size: 12px;
  background: var(--iap-input-bg);
  color: var(--iap-text-primary);
}
.run-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.run-item {
  padding: 10px 12px;
  border: 1px solid var(--iap-divider);
  border-radius: 10px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.1s, border-color 0.1s;
  background: var(--iap-surface-secondary);
}
.run-item:hover { background: var(--iap-surface-hover); }
.run-item.selected {
  border-color: var(--iap-input-border-focus);
  background: color-mix(in srgb, var(--iap-text-accent) 10%, var(--iap-surface-secondary));
}
.run-info { display: flex; align-items: center; gap: 8px; }
.run-time { color: var(--iap-text-tertiary); flex: 1; }
.run-meta { display: flex; gap: 8px; color: var(--iap-text-placeholder); margin-top: 4px; }
.run-status { font-weight: 600; font-size: 11px; padding: 2px 8px; border-radius: 999px; }
.status-running { background: color-mix(in srgb, var(--iap-text-accent) 12%, transparent); color: var(--iap-text-accent); }
.status-success { background: var(--iap-success-bg); color: var(--iap-success-text); }
.status-failed { background: var(--iap-error-bg); color: var(--iap-error-text); }
.status-cancelled { background: color-mix(in srgb, var(--iap-text-placeholder) 18%, transparent); color: var(--iap-text-tertiary); }
.pagination { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: var(--iap-text-tertiary); }
.pagination button {
  padding: 4px 10px;
  border: 1px solid var(--iap-divider);
  border-radius: 8px;
  background: var(--iap-surface-secondary);
  color: var(--iap-text-secondary);
  cursor: pointer;
}
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }
.trace-viewer {
  border: 1px solid var(--iap-divider);
  border-radius: 10px;
  padding: 10px;
  font-size: 12px;
  background: var(--iap-surface-secondary);
}
.trace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--iap-text-primary);
}
.btn-close {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--iap-text-tertiary);
}
.trace-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.trace-item {
  padding: 8px 10px;
  border-radius: 8px;
  border-left: 3px solid transparent;
  background: var(--iap-card-bg);
}
.trace-item.node-success { border-left-color: var(--iap-success-text); background: var(--iap-success-bg); }
.trace-item.node-failed { border-left-color: var(--iap-error-text); background: var(--iap-error-bg); }
.trace-item.node-skipped { border-left-color: var(--iap-text-placeholder); background: color-mix(in srgb, var(--iap-text-placeholder) 12%, transparent); color: var(--iap-text-placeholder); }
.trace-item.node-running { border-left-color: var(--iap-text-accent); background: color-mix(in srgb, var(--iap-text-accent) 10%, transparent); }
.trace-row { display: flex; align-items: center; gap: 6px; }
.trace-node-id { font-weight: 600; color: var(--iap-text-primary); }
.trace-node-type { color: var(--iap-text-tertiary); }
.trace-status-badge {
  margin-left: auto;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  font-weight: 600;
}
.trace-status-badge.node-success { background: color-mix(in srgb, var(--iap-success-text) 16%, transparent); color: var(--iap-success-text); }
.trace-status-badge.node-failed { background: color-mix(in srgb, var(--iap-error-text) 16%, transparent); color: var(--iap-error-text); }
.trace-status-badge.node-skipped { background: color-mix(in srgb, var(--iap-text-placeholder) 16%, transparent); color: var(--iap-text-tertiary); }
.trace-status-badge.node-running { background: color-mix(in srgb, var(--iap-text-accent) 16%, transparent); color: var(--iap-text-accent); }
.trace-meta { display: flex; gap: 6px; color: var(--iap-text-tertiary); margin-top: 4px; flex-wrap: wrap; }
.badge-cached {
  background: var(--iap-warning-bg);
  color: var(--iap-warning-text);
  padding: 0 6px;
  border-radius: 999px;
}
.badge-pushdown {
  background: var(--iap-ai-btn-bg);
  color: var(--iap-ai-btn-text);
  padding: 0 6px;
  border-radius: 999px;
}
.trace-error { color: var(--iap-error-text); margin-top: 4px; word-break: break-all; }
.trace-empty { color: var(--iap-text-placeholder); text-align: center; padding: 12px; }
.loading, .error { font-size: 12px; color: var(--iap-text-tertiary); text-align: center; padding: 20px; }
.error { color: var(--iap-error-text); }
</style>
