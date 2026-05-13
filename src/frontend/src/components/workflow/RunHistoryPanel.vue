<template>
  <div class="run-history-panel">
    <div class="panel-header">
      <span class="panel-title">执行历史</span>
      <button class="btn-refresh" @click="load">刷新</button>
    </div>

    <!-- 状态过滤 -->
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

    <!-- 选中执行的节点 Trace 展示 -->
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

const runs         = ref<WorkflowRunLogDTO[]>([])
const total        = ref(0)
const page         = ref(1)
const pageSize     = 20
const loading      = ref(false)
const traceLoading = ref(false)
const error        = ref<string | null>(null)
const filterStatus = ref('')
const selectedRunId = ref<string | null>(null)
const selectedRun   = ref<WorkflowRunLogDTO | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load() {
  loading.value = true
  error.value   = null
  try {
    const result = await listRuns(
      props.workflowId,
      page.value,
      pageSize,
      filterStatus.value || undefined,
    )
    runs.value  = result.items
    total.value = result.total
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function selectRun(runId: string) {
  if (selectedRunId.value === runId) {
    selectedRunId.value = null
    selectedRun.value   = null
    return
  }
  selectedRunId.value = runId
  traceLoading.value  = true
  try {
    selectedRun.value = await getRunDetail(props.workflowId, runId)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载详情失败'
  } finally {
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
    'status-running':   status === 'RUNNING',
    'status-success':   status === 'SUCCEEDED',
    'status-failed':    status === 'FAILED',
    'status-cancelled': status === 'CANCELLED',
  }
}

function nodeStatusClass(status: string) {
  return {
    'node-success': status === 'SUCCEEDED',
    'node-failed':  status === 'FAILED',
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
  background: #fff;
  border-left: 1px solid #e5e7eb;
  min-width: 280px;
  max-height: 100%;
  overflow-y: auto;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-title { font-weight: 600; font-size: 14px; }
.btn-refresh {
  padding: 4px 10px;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}
.filter-bar select {
  width: 100%;
  padding: 4px 6px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 12px;
}
.run-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.run-item {
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.1s;
}
.run-item:hover { background: #f9fafb; }
.run-item.selected { border-color: #3b82f6; background: #eff6ff; }
.run-info { display: flex; align-items: center; gap: 8px; }
.run-time { color: #6b7280; flex: 1; }
.run-meta { display: flex; gap: 8px; color: #9ca3af; margin-top: 2px; }
.run-status { font-weight: 600; font-size: 11px; padding: 1px 6px; border-radius: 999px; }
.status-running   { background: #dbeafe; color: #1d4ed8; }
.status-success   { background: #dcfce7; color: #15803d; }
.status-failed    { background: #fee2e2; color: #b91c1c; }
.status-cancelled { background: #f3f4f6; color: #6b7280; }
.pagination { display: flex; justify-content: space-between; align-items: center; font-size: 12px; }
.pagination button {
  padding: 2px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }

/* Trace */
.trace-viewer {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
  background: #fafafa;
}
.trace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}
.btn-close {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: #6b7280;
}
.trace-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.trace-item {
  padding: 6px 8px;
  border-radius: 4px;
  border-left: 3px solid transparent;
}
.trace-item.node-success { border-left-color: #22c55e; background: #f0fdf4; }
.trace-item.node-failed  { border-left-color: #ef4444; background: #fef2f2; }
.trace-item.node-skipped { border-left-color: #d1d5db; background: #f9fafb; color: #9ca3af; }
.trace-item.node-running { border-left-color: #3b82f6; background: #eff6ff; }
.trace-row { display: flex; align-items: center; gap: 6px; }
.trace-node-id   { font-weight: 600; }
.trace-node-type { color: #6b7280; }
.trace-status-badge {
  margin-left: auto;
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 999px;
  font-weight: 600;
}
.trace-status-badge.node-success { background: #dcfce7; color: #15803d; }
.trace-status-badge.node-failed  { background: #fee2e2; color: #b91c1c; }
.trace-status-badge.node-skipped { background: #f3f4f6; color: #6b7280; }
.trace-status-badge.node-running { background: #dbeafe; color: #1d4ed8; }
.trace-meta { display: flex; gap: 6px; color: #6b7280; margin-top: 2px; flex-wrap: wrap; }
.badge-cached  { background: #fef9c3; color: #a16207; padding: 0 4px; border-radius: 999px; }
.badge-pushdown { background: #ede9fe; color: #6d28d9; padding: 0 4px; border-radius: 999px; }
.trace-error { color: #b91c1c; margin-top: 2px; word-break: break-all; }
.trace-empty { color: #9ca3af; text-align: center; padding: 12px; }
.loading, .error { font-size: 12px; color: #6b7280; text-align: center; padding: 20px; }
.error { color: #dc2626; }
</style>
