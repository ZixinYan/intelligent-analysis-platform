<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { DatasourceTestConnectionResultDTO } from '@/types/contract'
import DatasourceForm from './DatasourceForm.vue'
import { useDatasourceStore } from '@/stores/datasource'

const store = useDatasourceStore()
const formVisible = ref(false)
const editingId = ref<string>()
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

          <div v-if="testResults[datasource.id ?? datasource.datasourceId]" class="alert" :class="testResults[datasource.id ?? datasource.datasourceId]?.success ? 'alert--success' : 'alert--error'">
            {{ testResults[datasource.id ?? datasource.datasourceId]?.message || (testResults[datasource.id ?? datasource.datasourceId]?.success ? '连接成功' : '连接失败') }}
            <span v-if="testResults[datasource.id ?? datasource.datasourceId]?.latencyMs !== undefined"> · {{ testResults[datasource.id ?? datasource.datasourceId]?.latencyMs }}ms</span>
          </div>
          <div v-if="actionErrors[datasource.id ?? datasource.datasourceId]" class="alert alert--error">{{ actionErrors[datasource.id ?? datasource.datasourceId] }}</div>
        </div>

        <div class="datasource-card__actions">
          <button class="ghost-button" type="button" :disabled="testLoading[datasource.id ?? datasource.datasourceId] || deleteLoading[datasource.id ?? datasource.datasourceId]" @click="openEdit(datasource.id ?? datasource.datasourceId)">编辑</button>
          <button class="ghost-button" type="button" :disabled="testLoading[datasource.id ?? datasource.datasourceId] || deleteLoading[datasource.id ?? datasource.datasourceId]" @click="handleTest(datasource.id ?? datasource.datasourceId)">
            {{ testLoading[datasource.id ?? datasource.datasourceId] ? '测试中...' : '测试连接' }}
          </button>
          <button class="danger-button" type="button" :disabled="deleteLoading[datasource.id ?? datasource.datasourceId] || testLoading[datasource.id ?? datasource.datasourceId]" @click="handleDelete(datasource.id ?? datasource.datasourceId)">
            {{ deleteLoading[datasource.id ?? datasource.datasourceId] ? '删除中...' : '删除' }}
          </button>
        </div>
      </article>
    </div>

    <DatasourceForm :visible="formVisible" :editing-id="editingId" @close="closeForm" @saved="loadDatasources" />
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
  color: var(--iap-text-primary);
}
.page-header p {
  margin: 6px 0 0;
  color: var(--iap-text-tertiary);
}
.page-state {
  border: 1px dashed var(--iap-divider-strong);
  border-radius: 16px;
  padding: 32px;
  color: var(--iap-text-tertiary);
  text-align: center;
  background: var(--iap-card-bg);
}
.page-state--error {
  color: var(--iap-error-text);
  border-color: var(--iap-error-border);
  background: var(--iap-error-bg);
}
.card-list {
  display: grid;
  gap: 16px;
}
.datasource-card {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  border: 1px solid var(--iap-card-border);
  border-radius: 18px;
  background: var(--iap-card-bg);
  padding: 20px;
  box-shadow: var(--iap-shadow-panel);
}
.datasource-card__title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.datasource-card__title-row h3 {
  margin: 0;
  font-size: 18px;
  color: var(--iap-text-primary);
}
.datasource-card__meta {
  margin: 8px 0 0;
  color: var(--iap-text-tertiary);
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
  border: 1px solid var(--iap-divider);
}
.status-chip--active {
  color: var(--iap-success-text);
  background: var(--iap-success-bg);
}
.status-chip--unreachable {
  color: var(--iap-error-text);
  background: var(--iap-error-bg);
}
.status-chip--inactive {
  color: var(--iap-warning-text);
  background: var(--iap-warning-bg);
}
.alert {
  margin-top: 12px;
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 14px;
  border: 1px solid transparent;
}
.alert--success {
  background: var(--iap-success-bg);
  border-color: var(--iap-success-border);
  color: var(--iap-success-text);
}
.alert--error {
  background: var(--iap-error-bg);
  border-color: var(--iap-error-border);
  color: var(--iap-error-text);
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
  background: var(--iap-btn-primary-bg);
  color: var(--iap-btn-primary-text);
}
.ghost-button {
  border: 1px solid var(--iap-btn-secondary-border);
  background: var(--iap-btn-secondary-bg);
  color: var(--iap-btn-secondary-text);
}
.danger-button {
  border: 1px solid var(--iap-btn-danger-border);
  background: var(--iap-btn-danger-bg);
  color: var(--iap-btn-danger-text);
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
