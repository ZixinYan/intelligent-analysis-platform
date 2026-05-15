<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import type { ApprovalRequestDTO, ApprovalStatus } from '@/types/contract'
import { approveRequest, listApprovals, rejectRequest } from '@/api/approval'

const STATUS_OPTIONS: { label: string; value: string }[] = [
  { label: '全部', value: '' },
  { label: '待审批', value: 'PENDING' },
  { label: '已批准', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' },
]

const statusFilter = ref<string>('PENDING')
const approvals = ref<ApprovalRequestDTO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string>()

// 决策弹窗状态
const decision = ref<{ request: ApprovalRequestDTO; type: 'approve' | 'reject' } | null>(null)
const comment = ref('')
const deciding = ref(false)
const decisionError = ref<string>()

async function load() {
  loading.value = true
  error.value = undefined
  try {
    const result = await listApprovals({
      status: (statusFilter.value || undefined) as ApprovalStatus | undefined,
      pageSize: 100,
    })
    approvals.value = result.items
    total.value = result.total
  }
  catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
  finally {
    loading.value = false
  }
}

async function submitDecision() {
  if (!decision.value)
    return
  deciding.value = true
  decisionError.value = undefined
  try {
    const { request, type } = decision.value
    if (type === 'approve') {
      await approveRequest(request.requestId, { comment: comment.value || undefined })
    }
    else {
      await rejectRequest(request.requestId, { comment: comment.value || undefined })
    }
    closeModal()
    await load()
  }
  catch (err) {
    decisionError.value = err instanceof Error ? err.message : '操作失败'
  }
  finally {
    deciding.value = false
  }
}

function openDecision(req: ApprovalRequestDTO, type: 'approve' | 'reject') {
  decision.value = { request: req, type }
  comment.value = ''
  decisionError.value = undefined
}

function closeModal() {
  decision.value = null
  comment.value = ''
  decisionError.value = undefined
}

function statusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待审批', APPROVED: '已批准', REJECTED: '已拒绝' }
  return map[status] ?? status
}

function formatDate(ts: number) {
  return new Date(ts).toLocaleString('zh-CN')
}

let timer: ReturnType<typeof setInterval>
onMounted(() => {
  load()
  timer = setInterval(load, 30_000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <section class="approvals-page">
    <!-- 页头 -->
    <header class="page-header">
      <div>
        <h2>审批管理</h2>
        <p>处理工作流节点的人工审批请求</p>
      </div>
      <div class="filter-wrap">
        <select v-model="statusFilter" class="status-select" @change="load">
          <option v-for="opt in STATUS_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </div>
    </header>

    <!-- 内容区 -->
    <div v-if="loading && !approvals.length" class="page-state">加载中...</div>
    <div v-else-if="error" class="page-state page-state--error">{{ error }}</div>
    <div v-else-if="!approvals.length" class="page-state">暂无审批请求</div>
    <div v-else class="table-wrap">
      <table class="approval-table">
        <thead>
          <tr>
            <th>工作流</th>
            <th>节点</th>
            <th>原因</th>
            <th>状态</th>
            <th>申请时间</th>
            <th>到期时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="req in approvals" :key="req.requestId">
            <td class="cell-mono">{{ req.workflowId.slice(0, 8) }}…</td>
            <td class="cell-mono">{{ req.nodeId }}</td>
            <td class="cell-truncate cell-muted">{{ req.reason ?? '—' }}</td>
            <td>
              <span :class="['status-badge', `status-badge--${req.status.toLowerCase()}`]">
                {{ statusLabel(req.status) }}
              </span>
            </td>
            <td class="cell-muted">{{ formatDate(req.createdAt) }}</td>
            <td class="cell-muted">{{ req.expiresAt ? formatDate(req.expiresAt) : '—' }}</td>
            <td>
              <template v-if="req.status === 'PENDING'">
                <div class="action-row">
                  <button class="primary-button" @click="openDecision(req, 'approve')">批准</button>
                  <button class="danger-button" @click="openDecision(req, 'reject')">拒绝</button>
                </div>
              </template>
              <template v-else>
                <div class="cell-muted cell-sm">
                  {{ req.decidedBy ?? '—' }}
                  <template v-if="req.decisionComment"> · {{ req.decisionComment }}</template>
                </div>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 决策弹窗 -->
    <div v-if="decision" class="modal-overlay" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ decision.type === 'approve' ? '批准请求' : '拒绝请求' }}</h3>
          <button class="modal-close" @click="closeModal">✕</button>
        </div>
        <div class="modal-body">
          <div class="reason-box">
            <div class="reason-label">申请原因</div>
            <div class="reason-content">{{ decision.request.reason ?? '—' }}</div>
          </div>
          <div class="comment-wrap">
            <label class="comment-label">决策意见（选填）</label>
            <textarea
              v-model="comment"
              class="comment-input"
              rows="3"
              placeholder="填写批准/拒绝理由"
            />
          </div>
          <div v-if="decisionError" class="inline-error">{{ decisionError }}</div>
        </div>
        <div class="modal-footer">
          <button class="ghost-button" :disabled="deciding" @click="closeModal">取消</button>
          <button
            :class="decision.type === 'approve' ? 'primary-button' : 'danger-button'"
            :disabled="deciding"
            @click="submitDecision"
          >
            {{ deciding ? '提交中...' : `确认${decision.type === 'approve' ? '批准' : '拒绝'}` }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.approvals-page {
  min-height: calc(100vh - 72px);
  padding: 24px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
.page-header h2 {
  margin: 0;
  font-size: 24px;
}
.page-header p {
  margin: 6px 0 0;
  color: #94a3b8;
  font-size: 13px;
}
.filter-wrap {
  flex-shrink: 0;
}
.status-select {
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  color: #e2e8f0;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  min-width: 120px;
}
.page-state {
  border: 1px dashed #334155;
  border-radius: 16px;
  padding: 32px;
  color: #94a3b8;
  text-align: center;
}
.page-state--error {
  color: #fecaca;
  border-color: rgba(248, 113, 113, 0.35);
  background: rgba(127, 29, 29, 0.2);
}
.table-wrap {
  overflow: auto;
  border: 1px solid #1e293b;
  border-radius: 16px;
}
.approval-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.approval-table th,
.approval-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #1e293b;
}
.approval-table th {
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.95);
  font-weight: 500;
  white-space: nowrap;
}
.approval-table td {
  color: #e2e8f0;
}
.approval-table tbody tr:last-child td {
  border-bottom: none;
}
.approval-table tbody tr:hover td {
  background: rgba(255, 255, 255, 0.025);
}
.cell-mono {
  font-family: ui-monospace, monospace;
  font-size: 12px;
}
.cell-muted {
  color: #64748b !important;
}
.cell-sm {
  font-size: 12px;
}
.cell-truncate {
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.status-badge {
  display: inline-block;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 500;
}
.status-badge--pending {
  background: rgba(234, 179, 8, 0.18);
  color: #fde047;
}
.status-badge--approved {
  background: rgba(34, 197, 94, 0.18);
  color: #86efac;
}
.status-badge--rejected {
  background: rgba(248, 113, 113, 0.18);
  color: #fca5a5;
}
.action-row {
  display: flex;
  gap: 8px;
}
.primary-button,
.ghost-button,
.danger-button {
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  cursor: pointer;
}
.primary-button {
  border: none;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
}
.primary-button:disabled {
  opacity: 0.5;
  cursor: default;
}
.ghost-button {
  border: 1px solid #334155;
  background: transparent;
  color: #cbd5e1;
}
.ghost-button:disabled {
  opacity: 0.5;
  cursor: default;
}
.danger-button {
  border: 1px solid rgba(248, 113, 113, 0.35);
  background: rgba(127, 29, 29, 0.22);
  color: #fecaca;
}
.danger-button:disabled {
  opacity: 0.5;
  cursor: default;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.modal {
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 16px;
  width: 440px;
  max-width: 95vw;
  display: flex;
  flex-direction: column;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #1e293b;
}
.modal-header h3 {
  margin: 0;
  font-size: 16px;
}
.modal-close {
  background: none;
  border: none;
  color: #64748b;
  font-size: 16px;
  cursor: pointer;
  line-height: 1;
}
.modal-body {
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.reason-box {
  background: rgba(255, 255, 255, 0.04);
  border-radius: 8px;
  padding: 12px;
}
.reason-label {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}
.reason-content {
  font-size: 13px;
  color: #e2e8f0;
}
.comment-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: #94a3b8;
}
.comment-input {
  width: 100%;
  box-sizing: border-box;
  background: #020617;
  border: 1px solid #334155;
  border-radius: 8px;
  color: #e2e8f0;
  padding: 10px 12px;
  font-size: 13px;
  resize: vertical;
  font-family: inherit;
}
.comment-input:focus {
  outline: none;
  border-color: #2563eb;
}
.inline-error {
  color: #fca5a5;
  font-size: 12px;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 24px 20px;
  border-top: 1px solid #1e293b;
}
</style>
