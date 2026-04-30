<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { previewQuery, validateQuery } from '@/api/query'
import SqlEditorField from '@/components/form/fields/SqlEditorField.vue'
import TablePreview from '@/components/output/TablePreview.vue'
import { useAsyncTask } from '@/composables/useAsyncTask'
import { useDatasourceStore } from '@/stores/datasource'
import type { PanelFieldDTO, QueryRequestDTO, QueryResultDTO, ValidateResultDTO } from '@/types/contract'

const store = useDatasourceStore()
const asyncTask = useAsyncTask()

const datasourceId = ref('')
const sql = ref('select 1 as sample')
const validating = ref(false)
const previewing = ref(false)
const validateResult = ref<ValidateResultDTO>()
const previewResult = ref<QueryResultDTO>()
const pageError = ref<string>()

const sqlField: PanelFieldDTO = {
  field: 'sql',
  label: 'SQL',
  componentType: 'SQL_EDITOR',
  placeholder: '请输入用于预览或异步执行的 SQL',
}

const statusText = computed(() => {
  if (asyncTask.status.value === 'PENDING') {
    return '任务已提交'
  }
  if (asyncTask.status.value === 'RUNNING') {
    return '任务执行中'
  }
  if (asyncTask.status.value === 'SUCCESS') {
    return '任务执行成功'
  }
  if (asyncTask.status.value === 'FAILED') {
    return '任务执行失败'
  }
  if (asyncTask.status.value === 'CANCELLED') {
    return '任务已取消'
  }
  return '等待执行'
})

const resultPayload = computed<QueryResultDTO | undefined>(() => {
  if (asyncTask.dataset.value) {
    return {
      queryId: asyncTask.taskId.value || 'async-task-result',
      status: asyncTask.status.value || 'SUCCESS',
      dataset: asyncTask.dataset.value,
      result: {
        kind: 'DATASET',
        dataset: asyncTask.dataset.value,
      },
    }
  }
  return previewResult.value
})

function resetResults(options: { clearValidation?: boolean } = {}) {
  previewResult.value = undefined
  asyncTask.reset()
  if (options.clearValidation) {
    validateResult.value = undefined
  }
}

function buildRequest(): QueryRequestDTO {
  return {
    datasourceId: datasourceId.value,
    sql: sql.value,
    option: {
      timeoutMs: 10000,
      limit: 500,
      useCache: true,
    },
  }
}

function validateInput() {
  if (!datasourceId.value) {
    pageError.value = '请选择数据源'
    return false
  }
  if (!sql.value.trim()) {
    pageError.value = '请输入 SQL'
    return false
  }
  pageError.value = undefined
  return true
}

async function handleValidate() {
  if (!validateInput()) {
    return
  }
  validating.value = true
  pageError.value = undefined
  resetResults({ clearValidation: true })
  try {
    validateResult.value = await validateQuery(buildRequest())
  }
  catch (err) {
    pageError.value = err instanceof Error ? err.message : 'SQL 校验失败'
  }
  finally {
    validating.value = false
  }
}

async function handlePreview() {
  if (!validateInput()) {
    return
  }
  previewing.value = true
  pageError.value = undefined
  resetResults()
  try {
    previewResult.value = await previewQuery(buildRequest())
  }
  catch (err) {
    pageError.value = err instanceof Error ? err.message : 'SQL 预览失败'
  }
  finally {
    previewing.value = false
  }
}

async function handleSubmitAsync() {
  if (!validateInput()) {
    return
  }
  pageError.value = undefined
  resetResults()
  try {
    await asyncTask.submit(buildRequest())
  }
  catch (err) {
    pageError.value = err instanceof Error ? err.message : '异步任务提交失败'
  }
}

async function handleCancel() {
  pageError.value = undefined
  try {
    await asyncTask.cancel()
  }
  catch (err) {
    pageError.value = err instanceof Error ? err.message : '取消任务失败'
  }
}

onMounted(() => {
  store.load().catch(() => undefined)
})
</script>

<template>
  <section class="query-page">
    <header class="page-header">
      <div>
        <h2>SQL 查询工作台</h2>
        <p>完成 validate / preview / async submit / poll / cancel 全链路联调。</p>
      </div>
    </header>

    <div class="query-layout">
      <section class="panel">
        <div class="field">
          <span>数据源</span>
          <select v-model="datasourceId" class="field-input">
            <option value="">请选择</option>
            <option v-for="option in store.options" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </div>

        <div class="field">
          <span>SQL</span>
          <SqlEditorField :field="sqlField" :model-value="sql" @update:model-value="sql = String($event)" />
        </div>

        <div class="toolbar">
          <button class="ghost-button" type="button" :disabled="validating || previewing" @click="handleValidate">
            {{ validating ? '校验中...' : 'Validate SQL' }}
          </button>
          <button class="ghost-button" type="button" :disabled="validating || previewing" @click="handlePreview">
            {{ previewing ? '预览中...' : 'Preview' }}
          </button>
          <button class="primary-button" type="button" :disabled="asyncTask.loading.value" @click="handleSubmitAsync">
            {{ asyncTask.loading.value ? '提交中...' : '提交异步任务' }}
          </button>
          <button class="danger-button" type="button" :disabled="!asyncTask.isRunning.value" @click="handleCancel">取消</button>
        </div>

        <div v-if="validateResult" class="alert" :class="validateResult.valid ? 'alert--success' : 'alert--error'">
          {{ validateResult.valid ? 'SQL 校验通过' : (validateResult.message || 'SQL 校验未通过') }}
        </div>
        <div v-if="pageError" class="alert alert--error">{{ pageError }}</div>
        <div v-if="asyncTask.error.value?.message" class="alert alert--error">{{ asyncTask.error.value.message }}</div>
      </section>

      <section class="panel panel--result">
        <div class="status-block">
          <div>
            <span class="status-block__label">异步任务状态</span>
            <strong>{{ statusText }}</strong>
          </div>
          <div class="progress-bar">
            <div class="progress-bar__value" :style="{ width: `${Math.max(asyncTask.progress.value, asyncTask.status.value === 'SUCCESS' ? 100 : 0)}%` }" />
          </div>
          <small v-if="asyncTask.taskId.value" class="status-block__meta">Task ID: {{ asyncTask.taskId.value }}</small>
        </div>

        <div class="result-block">
          <div class="result-block__header">
            <h3>查询结果</h3>
            <span v-if="resultPayload?.dataset?.rows?.length">{{ resultPayload.dataset.rows.length }} 行</span>
          </div>
          <TablePreview :result="resultPayload" />
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.query-page {
  min-height: calc(100vh - 72px);
  padding: 24px;
}
.page-header {
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
.query-layout {
  display: grid;
  grid-template-columns: minmax(0, 520px) minmax(0, 1fr);
  gap: 20px;
}
.panel {
  border: 1px solid #1e293b;
  border-radius: 18px;
  background: #0f172a;
  padding: 20px;
}
.panel--result {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}
.field span {
  color: #cbd5e1;
  font-size: 14px;
}
.field-input {
  width: 100%;
  border: 1px solid #334155;
  border-radius: 12px;
  background: #020617;
  color: #e2e8f0;
  padding: 10px 12px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.status-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.status-block__label,
.status-block__meta {
  color: #94a3b8;
}
.progress-bar {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: #1e293b;
  overflow: hidden;
}
.progress-bar__value {
  height: 100%;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  transition: width 0.2s ease;
}
.result-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 320px;
}
.result-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.result-block__header h3 {
  margin: 0;
}
.result-block__header span {
  color: #94a3b8;
}
.alert {
  margin-top: 16px;
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
@media (max-width: 1080px) {
  .query-layout {
    grid-template-columns: 1fr;
  }
}
</style>
