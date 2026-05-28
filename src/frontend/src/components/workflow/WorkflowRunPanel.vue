<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import OutputRenderer from '@/components/output/OutputRenderer.vue'
import { useWorkflowStore, useWorkflowDebugStore, useWorkflowGraphStore } from '@/stores/workflow'

const workflow = useWorkflowStore()
const debugStore = useWorkflowDebugStore()
const graphStore = useWorkflowGraphStore()
const { isStreaming, streamError, workflowNodeStates } = storeToRefs(debugStore)
const workflowId = computed(() => workflow.workflowId)

/** 节点列表：按画布顺序排列，附带运行状态 */
const nodeRows = computed(() => {
  return graphStore.nodes.map(node => {
    const state = workflowNodeStates.value.get(node.id)
    return {
      id: node.id,
      title: node.data.title,
      nodeType: node.data.type,
      state,
    }
  })
})

const hasAnyState = computed(() => workflowNodeStates.value.size > 0)

/** 整体完成状态 */
const overallStatus = computed(() => {
  if (isStreaming.value) return 'running'
  if (!hasAnyState.value) return 'idle'
  if (streamError.value) return 'error'
  const states = [...workflowNodeStates.value.values()]
  if (states.some(s => s.status === 'error')) return 'error'
  if (states.every(s => s.status === 'success' || s.status === 'skipped')) return 'success'
  return 'done'
})

const totalNodes = computed(() => nodeRows.value.length)
const succeededCount = computed(() => [...workflowNodeStates.value.values()].filter(s => s.status === 'success').length)
const failedCount = computed(() => [...workflowNodeStates.value.values()].filter(s => s.status === 'error').length)

/** 展开某个节点查看输出 */
const expandedNodeId = ref<string | null>(null)
function toggleExpand(nodeId: string) {
  expandedNodeId.value = expandedNodeId.value === nodeId ? null : nodeId
}

function handleRun() {
  if (workflowId.value) {
    debugStore.runWorkflow(workflowId.value)
  }
}

function handleStop() {
  debugStore.stopWorkflow()
}

function elapsedLabel(ms?: number) {
  if (ms === undefined) return ''
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s`
}

const STATUS_ICON: Record<string, string> = {
  pending: '○',
  running: '◌',
  success: '✓',
  error: '✕',
  skipped: '—',
}
const STATUS_CLASS: Record<string, string> = {
  pending: 'wrp__node-status--pending',
  running: 'wrp__node-status--running',
  success: 'wrp__node-status--success',
  error: 'wrp__node-status--error',
  skipped: 'wrp__node-status--skipped',
}
</script>

<template>
  <aside class="wrp">
    <!-- Header -->
    <header class="wrp__header">
      <div class="wrp__title">运行工作流</div>
      <div class="wrp__header-actions">
        <button
          v-if="!isStreaming"
          class="wrp__btn wrp__btn--primary"
          :disabled="!workflowId"
          :title="!workflowId ? '请先保存工作流' : '运行整个工作流'"
          @click="handleRun"
        >
          ▷ 运行
        </button>
        <button v-else class="wrp__btn wrp__btn--stop" @click="handleStop">
          ■ 停止
        </button>
      </div>
    </header>

    <!-- Overall status bar -->
    <div
      v-if="hasAnyState || isStreaming"
      class="wrp__summary"
      :class="{
        'wrp__summary--running': overallStatus === 'running',
        'wrp__summary--success': overallStatus === 'success',
        'wrp__summary--error': overallStatus === 'error',
        'wrp__summary--done': overallStatus === 'done',
      }"
    >
      <span v-if="isStreaming" class="wrp__spinner" />
      <span class="wrp__summary-label">
        <template v-if="isStreaming">执行中…</template>
        <template v-else-if="overallStatus === 'success'">全部节点成功</template>
        <template v-else-if="overallStatus === 'error'">有节点执行失败</template>
        <template v-else>执行完成</template>
      </span>
      <span class="wrp__summary-counts">
        {{ succeededCount }}/{{ totalNodes }} 节点完成
        <span v-if="failedCount > 0" class="wrp__summary-failed">{{ failedCount }} 失败</span>
      </span>
    </div>

    <!-- Workflow-level error -->
    <div v-if="streamError" class="wrp__global-error">
      <span class="wrp__error-code">{{ streamError.code ?? 'ERROR' }}</span>
      <span class="wrp__error-msg">{{ streamError.message ?? '工作流执行失败' }}</span>
    </div>

    <!-- Empty state -->
    <div v-if="!hasAnyState && !isStreaming" class="wrp__empty">
      <div class="wrp__empty-icon">▷</div>
      <div class="wrp__empty-text">点击「运行」执行整个工作流</div>
      <div v-if="!workflowId" class="wrp__empty-hint">请先保存工作流</div>
    </div>

    <!-- Node list -->
    <ul v-if="hasAnyState || isStreaming" class="wrp__node-list">
      <li
        v-for="row in nodeRows"
        :key="row.id"
        class="wrp__node-item"
        :class="{ 'wrp__node-item--expanded': expandedNodeId === row.id }"
      >
        <button class="wrp__node-row" @click="row.state && toggleExpand(row.id)">
          <!-- Status icon -->
          <span
            class="wrp__node-status"
            :class="row.state ? STATUS_CLASS[row.state.status] : 'wrp__node-status--pending'"
          >
            <span v-if="row.state?.status === 'running'" class="wrp__mini-spinner" />
            <span v-else>{{ row.state ? STATUS_ICON[row.state.status] : STATUS_ICON.pending }}</span>
          </span>

          <!-- Node name -->
          <span class="wrp__node-name">{{ row.title }}</span>
          <span class="wrp__node-type">{{ row.nodeType }}</span>

          <!-- Elapsed -->
          <span v-if="row.state?.elapsedMs !== undefined" class="wrp__node-elapsed">
            {{ elapsedLabel(row.state.elapsedMs) }}
          </span>

          <!-- Expand chevron -->
          <span v-if="row.state?.result || row.state?.error" class="wrp__chevron">
            {{ expandedNodeId === row.id ? '▾' : '▸' }}
          </span>
        </button>

        <!-- Error detail -->
        <div v-if="expandedNodeId === row.id && row.state?.error" class="wrp__node-detail">
          <div class="wrp__node-error">
            <div class="wrp__error-code">{{ row.state.error.code ?? 'ERROR' }}</div>
            <div class="wrp__error-msg">{{ row.state.error.message ?? '节点执行失败' }}</div>
          </div>
        </div>

        <!-- Result output -->
        <div v-if="expandedNodeId === row.id && row.state?.result && row.state.status === 'success'" class="wrp__node-detail">
          <OutputRenderer :result="row.state.result" mode="preview" />
        </div>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.wrp {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  height: 100%;
  border-left: 1px solid var(--iap-divider);
  background: var(--iap-panel-bg);
  overflow: hidden;
}

.wrp__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--iap-divider);
  flex-shrink: 0;
  background: var(--iap-panel-bg);
}

.wrp__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--iap-text-primary);
}

.wrp__header-actions {
  display: flex;
  gap: 8px;
}

.wrp__btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: background 0.15s;
}

.wrp__btn--primary {
  background: var(--iap-btn-primary-bg);
  color: var(--iap-btn-primary-text);
}

.wrp__btn--primary:hover:not(:disabled) {
  background: var(--iap-btn-primary-hover);
}

.wrp__btn--primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.wrp__btn--stop {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.wrp__btn--stop:hover {
  background: rgba(239, 68, 68, 0.2);
}

/* Summary bar */
.wrp__summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 12px;
  border-bottom: 1px solid var(--iap-divider);
  flex-shrink: 0;
}

.wrp__summary--running { background: rgba(59, 130, 246, 0.07); color: #60a5fa; }
.wrp__summary--success { background: rgba(34, 197, 94, 0.07); color: #22c55e; }
.wrp__summary--error   { background: rgba(239, 68, 68, 0.07); color: #ef4444; }
.wrp__summary--done    { background: rgba(100, 116, 139, 0.07); color: var(--iap-text-secondary); }

.wrp__summary-label { font-weight: 600; }

.wrp__summary-counts {
  margin-left: auto;
  font-size: 11px;
  opacity: 0.8;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}

.wrp__summary-failed {
  margin-left: 6px;
  color: #ef4444;
}

/* Global error */
.wrp__global-error {
  margin: 10px 16px 0;
  padding: 10px 12px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}

/* Empty */
.wrp__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--iap-text-placeholder);
  font-size: 13px;
  padding: 32px 16px;
}

.wrp__empty-icon { font-size: 28px; opacity: 0.4; }
.wrp__empty-text { text-align: center; line-height: 1.6; }
.wrp__empty-hint { font-size: 11px; color: var(--iap-text-disabled); }

/* Node list */
.wrp__node-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
  margin: 0;
  list-style: none;
}

.wrp__node-item {
  border-bottom: 1px solid rgba(var(--iap-divider-rgb, 226, 232, 240), 0.5);
}

.wrp__node-item:last-child {
  border-bottom: none;
}

.wrp__node-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s;
}

.wrp__node-row:hover {
  background: var(--iap-hover-bg, rgba(0,0,0,0.04));
}

/* Status indicator */
.wrp__node-status {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}

.wrp__node-status--pending { color: var(--iap-text-disabled); }
.wrp__node-status--running { color: #60a5fa; }
.wrp__node-status--success { color: #22c55e; background: rgba(34, 197, 94, 0.12); }
.wrp__node-status--error   { color: #ef4444; background: rgba(239, 68, 68, 0.12); }
.wrp__node-status--skipped { color: var(--iap-text-tertiary); }

.wrp__node-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--iap-text-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.wrp__node-type {
  font-size: 10px;
  color: var(--iap-text-tertiary);
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  white-space: nowrap;
  flex-shrink: 0;
}

.wrp__node-elapsed {
  font-size: 11px;
  color: var(--iap-text-tertiary);
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  white-space: nowrap;
  flex-shrink: 0;
}

.wrp__chevron {
  font-size: 11px;
  color: var(--iap-text-tertiary);
  flex-shrink: 0;
}

/* Node detail (expanded) */
.wrp__node-detail {
  padding: 0 16px 12px;
}

.wrp__node-error {
  padding: 8px 10px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.15);
  border-radius: 8px;
}

.wrp__error-code {
  font-size: 11px;
  font-weight: 700;
  color: #ef4444;
  font-family: ui-monospace, monospace;
  margin-bottom: 3px;
}

.wrp__error-msg {
  font-size: 12px;
  color: #fca5a5;
  line-height: 1.5;
}

/* Spinners */
.wrp__spinner {
  display: inline-block;
  width: 13px;
  height: 13px;
  border: 2px solid rgba(96, 165, 250, 0.3);
  border-top-color: #60a5fa;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}

.wrp__mini-spinner {
  display: inline-block;
  width: 10px;
  height: 10px;
  border: 1.5px solid rgba(96, 165, 250, 0.3);
  border-top-color: #60a5fa;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }
</style>
