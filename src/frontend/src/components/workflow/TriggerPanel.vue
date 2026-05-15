<template>
  <div class="trigger-panel">
    <div class="panel-header">
      <span class="panel-title">触发器</span>
      <button class="btn-primary" @click="showCreateModal = true">+ 新建</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <ul v-else class="trigger-list">
      <li v-if="triggers.length === 0" class="empty">暂无触发器</li>
      <li v-for="t in triggers" :key="t.triggerId" class="trigger-item">
        <div class="trigger-header-row">
          <div class="trigger-type-label">
            <span class="type-badge">{{ t.triggerType === 'CRON' ? '定时' : 'Webhook' }}</span>
            <span class="trigger-status" :class="t.triggerStatus === 'ACTIVE' ? 'status-active' : 'status-paused'">
              {{ t.triggerStatus === 'ACTIVE' ? '运行中' : '已暂停' }}
            </span>
          </div>
          <div class="trigger-actions">
            <button @click="doFire(t.triggerId)">立即执行</button>
            <button @click="doToggle(t)">{{ t.triggerStatus === 'ACTIVE' ? '暂停' : '启用' }}</button>
            <button class="btn-danger" @click="doDelete(t.triggerId)">删除</button>
          </div>
        </div>
        <div v-if="t.cronExpr" class="trigger-meta">Cron: {{ t.cronExpr }}</div>
        <div v-if="t.webhookUrl" class="trigger-meta trigger-url">URL: {{ t.webhookUrl }}</div>
        <div v-if="t.nextFireAt" class="trigger-meta">
          下次执行：{{ formatTime(t.nextFireAt) }}
        </div>
        <div v-if="t.lastFireAt" class="trigger-meta">
          上次执行：{{ formatTime(t.lastFireAt) }}
          <span v-if="t.lastStatus" class="last-status" :class="t.lastStatus === 'SUCCEEDED' ? 'ok' : 'fail'">
            {{ t.lastStatus === 'SUCCEEDED' ? '成功' : '失败' }}
          </span>
        </div>
      </li>
    </ul>

    <!-- 新建触发器弹窗 -->
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal">
        <div class="modal-header">
          <span>新建触发器</span>
          <button class="btn-close" @click="showCreateModal = false">✕</button>
        </div>

        <div class="form-group">
          <label>触发器类型</label>
          <select v-model="newType">
            <option value="CRON">定时触发（Cron）</option>
            <option value="WEBHOOK">Webhook</option>
          </select>
        </div>

        <div v-if="newType === 'CRON'" class="form-group">
          <label>Cron 表达式</label>
          <input v-model="newCronExpr" placeholder="例：0 9 * * *（每天 9:00）" />
          <div class="form-hint">格式：分 时 日 月 周（UTC）</div>
        </div>

        <div v-if="newType === 'WEBHOOK'" class="form-hint-box">
          创建后系统将自动生成 Webhook URL 和 Token。
        </div>

        <div class="modal-footer">
          <button @click="showCreateModal = false">取消</button>
          <button class="btn-primary" :disabled="creating" @click="doCreate">
            {{ creating ? '创建中...' : '创建' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { TriggerDTO, TriggerType } from '@/types/contract'
import {
  listTriggers,
  createTrigger,
  deleteTrigger,
  updateTriggerStatus,
  fireTrigger,
} from '@/api/workflow'

const props = defineProps<{ workflowId: string }>()

const triggers = ref<TriggerDTO[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const showCreateModal = ref(false)
const creating = ref(false)
const newType = ref<TriggerType>('CRON')
const newCronExpr = ref('0 9 * * *')

async function load() {
  loading.value = true
  error.value = null
  try {
    triggers.value = await listTriggers(props.workflowId)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function doCreate() {
  creating.value = true
  try {
    await createTrigger(props.workflowId, {
      triggerType: newType.value,
      cronExpr: newType.value === 'CRON' ? newCronExpr.value : undefined,
    })
    showCreateModal.value = false
    newCronExpr.value = '0 9 * * *'
    await load()
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '创建失败')
  } finally {
    creating.value = false
  }
}

async function doToggle(t: TriggerDTO) {
  try {
    await updateTriggerStatus(t.triggerId, t.triggerStatus === 'ACTIVE' ? 'PAUSED' : 'ACTIVE')
    await load()
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '操作失败')
  }
}

async function doDelete(triggerId: string) {
  if (!confirm('确认删除该触发器？')) return
  try {
    await deleteTrigger(triggerId)
    await load()
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '删除失败')
  }
}

async function doFire(triggerId: string) {
  try {
    await fireTrigger(triggerId)
    alert('已触发执行，请稍后查看运行记录')
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '触发失败')
  }
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleString()
}

onMounted(load)
</script>

<style scoped>
.trigger-panel {
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
.btn-primary {
  padding: 4px 10px;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.trigger-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.empty { font-size: 12px; color: #9ca3af; text-align: center; padding: 20px; }
.trigger-item {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.trigger-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.trigger-type-label { display: flex; align-items: center; gap: 6px; }
.type-badge {
  padding: 1px 7px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 600;
  font-size: 11px;
}
.trigger-status { font-size: 11px; }
.status-active { color: #16a34a; }
.status-paused { color: #9ca3af; }
.trigger-actions { display: flex; gap: 4px; }
.trigger-actions button {
  padding: 2px 7px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 11px;
}
.trigger-actions button:hover { background: #f9fafb; }
.btn-danger { color: #dc2626 !important; border-color: #fca5a5 !important; }
.btn-danger:hover { background: #fef2f2 !important; }
.trigger-meta { color: #6b7280; }
.trigger-url { word-break: break-all; }
.last-status { margin-left: 4px; font-weight: 600; }
.last-status.ok { color: #16a34a; }
.last-status.fail { color: #dc2626; }

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  width: 360px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.15);
}
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
}
.btn-close {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: #6b7280;
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.form-group label { font-size: 12px; font-weight: 500; color: #374151; }
.form-group select,
.form-group input {
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 7px 10px;
  font-size: 13px;
  outline: none;
}
.form-group input:focus,
.form-group select:focus { border-color: #3b82f6; }
.form-hint { font-size: 11px; color: #9ca3af; }
.form-hint-box {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
  color: #6b7280;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.modal-footer button {
  padding: 6px 14px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}
.loading, .error { font-size: 12px; color: #6b7280; text-align: center; padding: 20px; }
.error { color: #dc2626; }
</style>
