<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { SavedDatasetSummaryDTO } from '@/types/contract'
import { deleteDataset, listDatasets } from '@/api/dataset'
import { downloadExportFile, triggerExport, waitForExport } from '@/api/export'
import DatasetDetail from './DatasetDetail.vue'

const datasets = ref<SavedDatasetSummaryDTO[]>([])
const loading = ref(false)
const error = ref<string>()

const selectedId = ref<string>()
const deleteLoading = reactive<Record<string, boolean>>({})
const exportLoading = reactive<Record<string, boolean>>({})
const exportError = reactive<Record<string, string | undefined>>({})

async function load() {
  loading.value = true
  error.value = undefined
  try {
    const result = await listDatasets({ pageSize: 100 })
    datasets.value = result.items
  }
  catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
  finally {
    loading.value = false
  }
}

async function handleDelete(ds: SavedDatasetSummaryDTO) {
  if (!confirm(`确认删除「${ds.name}」？`))
    return
  deleteLoading[ds.datasetId] = true
  try {
    await deleteDataset(ds.datasetId)
    await load()
  }
  catch (err) {
    alert(err instanceof Error ? err.message : '删除失败')
  }
  finally {
    deleteLoading[ds.datasetId] = false
  }
}

async function handleExport(ds: SavedDatasetSummaryDTO) {
  exportLoading[ds.datasetId] = true
  exportError[ds.datasetId] = undefined
  try {
    const file = await triggerExport({ datasetId: ds.datasetId, format: 'csv', fileName: `${ds.name}.csv` })
    const ready = await waitForExport(file.fileId)
    await downloadExportFile(ready.fileId, ready.fileName)
  }
  catch (err) {
    exportError[ds.datasetId] = err instanceof Error ? err.message : '导出失败'
  }
  finally {
    exportLoading[ds.datasetId] = false
  }
}

function formatDate(ts: number) {
  return new Date(ts).toLocaleDateString('zh-CN')
}

onMounted(load)
</script>

<template>
  <!-- 详情视图 -->
  <DatasetDetail
    v-if="selectedId"
    :dataset-id="selectedId"
    @back="selectedId = undefined; load()"
  />

  <!-- 列表视图 -->
  <section v-else class="dataset-page">
    <header class="page-header">
      <div>
        <h2>数据集</h2>
        <p>Workflow 运行输出保存的结构化数据集</p>
      </div>
    </header>

    <div v-if="loading" class="page-state">加载中...</div>
    <div v-else-if="error" class="page-state page-state--error">{{ error }}</div>
    <div v-else-if="!datasets.length" class="page-state">暂无数据集。</div>
    <div v-else class="dataset-table-wrap">
      <table class="dataset-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>来源工作流</th>
            <th>行数</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ds in datasets" :key="ds.datasetId">
            <td>
              <button class="link-button" @click="selectedId = ds.datasetId">{{ ds.name }}</button>
            </td>
            <td class="cell-muted">{{ ds.sourceWorkflowId ?? '—' }}</td>
            <td>{{ ds.rowCount?.toLocaleString() ?? '—' }}</td>
            <td class="cell-muted">{{ formatDate(ds.updatedAt) }}</td>
            <td>
              <div class="action-row">
                <button class="ghost-button" @click="selectedId = ds.datasetId">查看</button>
                <button
                  class="ghost-button"
                  :disabled="exportLoading[ds.datasetId]"
                  @click="handleExport(ds)"
                >
                  {{ exportLoading[ds.datasetId] ? '导出中...' : '导出 CSV' }}
                </button>
                <button
                  class="danger-button"
                  :disabled="deleteLoading[ds.datasetId]"
                  @click="handleDelete(ds)"
                >
                  {{ deleteLoading[ds.datasetId] ? '删除中...' : '删除' }}
                </button>
              </div>
              <div v-if="exportError[ds.datasetId]" class="inline-error">{{ exportError[ds.datasetId] }}</div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.dataset-page {
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
.dataset-table-wrap {
  overflow: auto;
  border: 1px solid #1e293b;
  border-radius: 16px;
}
.dataset-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.dataset-table th,
.dataset-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #1e293b;
}
.dataset-table th {
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.95);
  font-weight: 500;
}
.dataset-table td {
  color: #e2e8f0;
}
.dataset-table tbody tr:last-child td {
  border-bottom: none;
}
.dataset-table tbody tr:hover td {
  background: rgba(255,255,255,0.025);
}
.cell-muted {
  color: #64748b !important;
}
.link-button {
  background: none;
  border: none;
  cursor: pointer;
  color: #60a5fa;
  font-size: 13px;
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.link-button:hover {
  color: #93c5fd;
}
.action-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.ghost-button,
.danger-button {
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
}
.ghost-button {
  border: 1px solid #334155;
  background: transparent;
  color: #cbd5e1;
}
.ghost-button:disabled {
  opacity: 0.45;
  cursor: default;
}
.danger-button {
  border: 1px solid rgba(248, 113, 113, 0.35);
  background: rgba(127, 29, 29, 0.22);
  color: #fecaca;
}
.danger-button:disabled {
  opacity: 0.45;
  cursor: default;
}
.inline-error {
  margin-top: 4px;
  color: #fca5a5;
  font-size: 11px;
}
</style>
