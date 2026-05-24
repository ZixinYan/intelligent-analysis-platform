<template>
  <div class="version-history-panel">
    <div class="panel-header">
      <span class="panel-title">版本历史</span>
      <button class="btn-snapshot" @click="createSnapshot">创建快照</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <ul v-else class="version-list">
      <li
        v-for="v in versions"
        :key="v.versionId"
        class="version-item"
        :class="{ published: v.published }"
      >
        <div class="version-info">
          <span class="version-number">v{{ v.versionNumber }}</span>
          <span class="version-summary">{{ v.changeSummary || '无说明' }}</span>
          <span v-if="v.published" class="badge-published">已发布</span>
        </div>
        <div class="version-meta">{{ formatTime(v.createdAt ?? 0) }}</div>
        <div class="version-actions">
          <button @click="viewVersion(v.versionNumber)">查看</button>
          <button @click="doPublish(v.versionNumber)">发布</button>
          <button @click="doRollback(v.versionNumber)">回滚</button>
          <button v-if="selectedForDiff !== null && selectedForDiff !== v.versionNumber"
                  @click="doDiff(v.versionNumber)">
            与 v{{ selectedForDiff }} 对比
          </button>
          <button v-else @click="selectForDiff(v.versionNumber)">选择对比</button>
        </div>
      </li>
    </ul>

    <div class="pagination">
      <button :disabled="page <= 1" @click="prevPage">上一页</button>
      <span>第 {{ page }} 页 / 共 {{ totalPages }} 页</span>
      <button :disabled="page >= totalPages" @click="nextPage">下一页</button>
    </div>

    <!-- Diff 展示 -->
    <div v-if="diffResult" class="diff-viewer">
      <h4>版本差异 v{{ diffResult.fromVersion }} → v{{ diffResult.toVersion }}</h4>
      <div v-if="diffResult.addedNodeIds.length" class="diff-section added">
        <strong>新增节点：</strong>{{ diffResult.addedNodeIds.join(', ') }}
      </div>
      <div v-if="diffResult.removedNodeIds.length" class="diff-section removed">
        <strong>删除节点：</strong>{{ diffResult.removedNodeIds.join(', ') }}
      </div>
      <div v-if="diffResult.modifiedNodeIds.length" class="diff-section modified">
        <strong>修改节点：</strong>{{ diffResult.modifiedNodeIds.join(', ') }}
      </div>
      <div v-if="diffResult.addedEdgeIds.length" class="diff-section added">
        <strong>新增连线：</strong>{{ diffResult.addedEdgeIds.join(', ') }}
      </div>
      <div v-if="diffResult.removedEdgeIds.length" class="diff-section removed">
        <strong>删除连线：</strong>{{ diffResult.removedEdgeIds.join(', ') }}
      </div>
      <button @click="diffResult = null">关闭</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  listVersions,
  snapshotVersion,
  publishVersion,
  rollbackVersion,
  diffVersions,
} from '@/api/workflow'
import type { WorkflowVersionDTO, WorkflowVersionDiffDTO } from '@/types/contract'

const props = defineProps<{ workflowId: string }>()
const emit = defineEmits<{ rollback: [versionNumber: number] }>()

const versions = ref<WorkflowVersionDTO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const error = ref<string | null>(null)
const diffResult = ref<WorkflowVersionDiffDTO | null>(null)
const selectedForDiff = ref<number | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await listVersions(props.workflowId, page.value, pageSize)
    versions.value = result.items
    total.value = result.total
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function createSnapshot() {
  try {
    await snapshotVersion(props.workflowId, '手动快照')
    await load()
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '快照创建失败')
  }
}

async function doPublish(versionNumber: number) {
  try {
    await publishVersion(props.workflowId, versionNumber)
    await load()
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '发布失败')
  }
}

async function doRollback(versionNumber: number) {
  try {
    await rollbackVersion(props.workflowId, versionNumber)
    emit('rollback', versionNumber)
    await load()
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '回滚失败')
  }
}

function selectForDiff(versionNumber: number) {
  selectedForDiff.value = versionNumber
}

async function doDiff(toVersionNumber: number) {
  if (selectedForDiff.value === null) return
  try {
    diffResult.value = await diffVersions(props.workflowId, selectedForDiff.value, toVersionNumber)
    selectedForDiff.value = null
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '对比失败')
  }
}

function viewVersion(versionNumber: number) {
  emit('rollback', versionNumber)
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

onMounted(load)
</script>

<style scoped>
.version-history-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  min-width: 280px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-title { font-weight: 600; font-size: 14px; }
.btn-snapshot {
  padding: 4px 10px;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}
.version-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.version-item {
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12px;
}
.version-item.published { border-color: #22c55e; background: #f0fdf4; }
.version-info { display: flex; align-items: center; gap: 6px; }
.version-number { font-weight: 700; color: #374151; }
.version-summary { color: #6b7280; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.badge-published {
  background: #22c55e; color: #fff;
  padding: 1px 6px; border-radius: 999px; font-size: 10px;
}
.version-meta { color: #9ca3af; margin-top: 2px; }
.version-actions { display: flex; gap: 4px; margin-top: 4px; flex-wrap: wrap; }
.version-actions button {
  padding: 2px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 11px;
}
.version-actions button:hover { background: #f9fafb; }
.pagination { display: flex; justify-content: space-between; align-items: center; font-size: 12px; }
.pagination button {
  padding: 2px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }
.diff-viewer {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
  background: #fafafa;
}
.diff-section { margin: 4px 0; }
.diff-section.added { color: #16a34a; }
.diff-section.removed { color: #dc2626; }
.diff-section.modified { color: #d97706; }
.loading, .error { font-size: 12px; color: #6b7280; text-align: center; padding: 20px; }
.error { color: #dc2626; }
</style>
