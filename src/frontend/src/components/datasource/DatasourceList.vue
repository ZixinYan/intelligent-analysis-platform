<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { DatasourceTestConnectionResultDTO } from '@/types/contract'
import DatasourceForm from './DatasourceForm.vue'
import TableBrowserModal from './TableBrowserModal.vue'
import { useDatasourceStore } from '@/stores/datasource'

const store = useDatasourceStore()
const formVisible = ref(false)
const editingId = ref<string>()
const browsingId = ref<string>()
const testLoading = reactive<Record<string, boolean>>({})
const deleteLoading = reactive<Record<string, boolean>>({})
const testResults = reactive<Record<string, DatasourceTestConnectionResultDTO | undefined>>({})
const actionErrors = reactive<Record<string, string | undefined>>({})

async function loadDatasources() {
  try {
    await store.load(true)
  }
  catch {
  }
}

function openCreate() {
  editingId.value = undefined
  formVisible.value = true
}

function openEdit(id: string) {
  editingId.value = id
  formVisible.value = true
}

function closeForm() {
  formVisible.value = false
}

function openBrowse(id: string) {
  browsingId.value = id
}

function closeBrowse() {
  browsingId.value = undefined
}

async function handleDelete(id: string) {
  deleteLoading[id] = true
  actionErrors[id] = undefined
  try {
    await store.remove(id)
    delete testResults[id]
  }
  catch (err) {
    actionErrors[id] = err instanceof Error ? err.message : '删除失败'
  }
  finally {
    deleteLoading[id] = false
  }
}

async function handleTest(id: string) {
  testLoading[id] = true
  actionErrors[id] = undefined
  try {
    testResults[id] = await store.test(id)
    await loadDatasources()
  }
  catch (err) {
    actionErrors[id] = err instanceof Error ? err.message : '测试连接失败'
  }
  finally {
    testLoading[id] = false
  }
}

onMounted(() => {
  loadDatasources()
})
</script>

<template>
  <section class="datasource-page">
    <header class="page-header">
      <div>
        <h2>数据源管理</h2>
        <p>统一管理分析数据源，支持测试连接与快速编辑。</p>
      </div>
      <button class="primary-button" type="button" @click="openCreate">新建数据源</button>
    </header>

    <div v-if="store.loading" class="page-state">加载中...</div>
    <div v-else-if="store.error" class="page-state page-state--error">{{ store.error }}</div>
    <div v-else-if="!store.datasources.length" class="page-state">暂无数据源，先创建一个。</div>
    <div v-else class="card-list">
      <article v-for="datasource in store.datasources" :key="datasource.id" class="datasource-card">
        <div class="datasource-card__main">
          <div class="datasource-card__title-row">
            <h3>{{ datasource.name }}</h3>
            <span class="status-chip" :class="`status-chip--${(datasource.status ?? 'ACTIVE').toLowerCase()}`">
              {{ datasource.status ?? 'ACTIVE' }}
            </span>
          </div>
          <p class="datasource-card__meta">{{ datasource.type }} · {{ datasource.host }}:{{ datasource.port }} / {{ datasource.database }}</p>
          <p class="datasource-card__meta">用户：{{ datasource.username }} · {{ datasource.readonly ? '只读' : '可写' }}</p>

          <div v-if="testResults[datasource.id]" class="alert" :class="testResults[datasource.id]?.success ? 'alert--success' : 'alert--error'">
            {{ testResults[datasource.id]?.message || (testResults[datasource.id]?.success ? '连接成功' : '连接失败') }}
            <span v-if="testResults[datasource.id]?.latencyMs !== undefined"> · {{ testResults[datasource.id]?.latencyMs }}ms</span>
          </div>
          <div v-if="actionErrors[datasource.id]" class="alert alert--error">{{ actionErrors[datasource.id] }}</div>
        </div>

        <div class="datasource-card__actions">
          <button class="ghost-button" type="button" :disabled="testLoading[datasource.id] || deleteLoading[datasource.id]" @click="openEdit(datasource.id)">编辑</button>
          <button class="ghost-button" type="button" :disabled="testLoading[datasource.id] || deleteLoading[datasource.id]" @click="handleTest(datasource.id)">
            {{ testLoading[datasource.id] ? '测试中...' : '测试连接' }}
          </button>
          <button class="ghost-button" type="button" :disabled="testLoading[datasource.id] || deleteLoading[datasource.id]" @click="openBrowse(datasource.id)">浏览表</button>
          <button class="danger-button" type="button" :disabled="deleteLoading[datasource.id] || testLoading[datasource.id]" @click="handleDelete(datasource.id)">
            {{ deleteLoading[datasource.id] ? '删除中...' : '删除' }}
          </button>
        </div>
      </article>
    </div>

    <DatasourceForm :visible="formVisible" :editing-id="editingId" @close="closeForm" @saved="loadDatasources" />
    <TableBrowserModal
      v-if="browsingId"
      :visible="Boolean(browsingId)"
      :datasource-id="browsingId"
      :datasource-name="store.findById(browsingId)?.name ?? browsingId"
      @close="closeBrowse"
    />
  </section>
</template>

<style scoped>
.datasource-page {
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
.card-list {
  display: grid;
  gap: 16px;
}
.datasource-card {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  border: 1px solid #1e293b;
  border-radius: 18px;
  background: #0f172a;
  padding: 20px;
}
.datasource-card__title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.datasource-card__title-row h3 {
  margin: 0;
  font-size: 18px;
}
.datasource-card__meta {
  margin: 8px 0 0;
  color: #94a3b8;
}
.datasource-card__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.status-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid #334155;
}
.status-chip--active {
  color: #86efac;
  background: rgba(20, 83, 45, 0.25);
}
.status-chip--unreachable {
  color: #fca5a5;
  background: rgba(127, 29, 29, 0.3);
}
.status-chip--inactive {
  color: #fde68a;
  background: rgba(133, 77, 14, 0.3);
}
.alert {
  margin-top: 12px;
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 14px;
}
.alert--success {
  background: rgba(20, 83, 45, 0.25);
  border: 1px solid rgba(74, 222, 128, 0.25);
  color: #bbf7d0;
}
.alert--error {
  background: rgba(127, 29, 29, 0.28);
  border: 1px solid rgba(248, 113, 113, 0.32);
  color: #fecaca;
}
.primary-button,
.ghost-button,
.danger-button {
  border-radius: 12px;
  padding: 10px 16px;
  cursor: pointer;
}
.primary-button {
  border: none;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
}
.ghost-button {
  border: 1px solid #334155;
  background: transparent;
  color: #cbd5e1;
}
.danger-button {
  border: 1px solid rgba(248, 113, 113, 0.35);
  background: rgba(127, 29, 29, 0.22);
  color: #fecaca;
}
@media (max-width: 900px) {
  .page-header,
  .datasource-card {
    grid-template-columns: 1fr;
  }
  .datasource-card__actions {
    flex-wrap: wrap;
  }
}
</style>
