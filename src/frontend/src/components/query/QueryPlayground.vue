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
  if (asyncTask.status.value === 'SUCCEEDED') {
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
      status: asyncTask.status.value || 'SUCCEEDED',
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
          <select v-model="datasourceId" class="field-input" :class="{ 'field-input--placeholder': !datasourceId }">
            <option value="" disabled hidden>请选择数据源</option>
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
            <div class="progress-bar__value" :style="{ width: `${Math.max(asyncTask.progress.value, asyncTask.status.value === 'SUCCEEDED' ? 100 : 0)}%` }" />
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
  color: var(--iap-text-tertiary);
}

.query-layout {
  display: grid;
  grid-template-columns: minmax(0, 520px) minmax(0, 1fr);
  gap: 20px;
}

.panel {
  border: 1px solid var(--iap-card-border);
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-panel-bg) 100%);
  padding: 20px;
  box-shadow: var(--iap-shadow-panel);
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
  color: var(--iap-text-secondary);
  font-size: 14px;
}

.field-input {
  width: 100%;
  min-height: 44px;
  border: 1px solid var(--iap-input-border);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-input-bg-focus) 100%);
  color: var(--iap-text-primary);
  padding: 11px 44px 11px 14px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-input-bg-focus) 100%);
  background-repeat: no-repeat, no-repeat;
  background-position: right 14px center, center;
  background-size: 14px 14px, auto;
  box-shadow: var(--iap-select-shadow);
  outline: none;
}

.field-input:hover {
  border-color: var(--iap-divider-strong);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-hover) 100%);
  transform: translateY(-1px);
}

.field-input:focus {
  border-color: var(--iap-input-border-focus);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%23155aef' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-focus) 100%);
  box-shadow: var(--iap-select-shadow), var(--iap-select-shadow-focus);
}

.field-input--placeholder {
  color: var(--iap-text-placeholder);
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
  color: var(--iap-text-tertiary);
}

.progress-bar {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: rgba(21, 90, 239, 0.08);
  overflow: hidden;
}

.progress-bar__value {
  height: 100%;
  background: linear-gradient(135deg, #155aef, #6e7bff);
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
  color: var(--iap-text-tertiary);
}

.alert {
  margin-top: 16px;
  border-radius: 14px;
  padding: 12px 14px;
  font-size: 14px;
}

.alert--success {
  background: var(--iap-success-bg);
  border: 1px solid var(--iap-success-border);
  color: var(--iap-success-text);
}

.alert--error {
  background: var(--iap-error-bg);
  border: 1px solid var(--iap-error-border);
  color: var(--iap-error-text);
}

.primary-button,
.ghost-button,
.danger-button {
  border-radius: 12px;
  padding: 10px 16px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease, border-color 0.15s ease;
}

.primary-button:hover:not(:disabled),
.ghost-button:hover:not(:disabled),
.danger-button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.primary-button {
  border: 1px solid transparent;
  background: linear-gradient(135deg, #155aef, #526dff);
  color: var(--iap-btn-primary-text);
  box-shadow: 0 12px 24px rgba(21, 90, 239, 0.18);
}

.primary-button:hover:not(:disabled) {
  box-shadow: 0 14px 28px rgba(21, 90, 239, 0.22);
}

.ghost-button {
  border: 1px solid var(--iap-btn-secondary-border);
  background: rgba(255, 255, 255, 0.82);
  color: var(--iap-text-secondary);
}

.ghost-button:hover:not(:disabled) {
  background: var(--iap-btn-secondary-hover);
  color: var(--iap-text-primary);
}

.danger-button {
  border: 1px solid var(--iap-btn-danger-border);
  background: var(--iap-btn-danger-bg);
  color: var(--iap-btn-danger-text);
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

@media (max-width: 1080px) {
  .query-layout {
    grid-template-columns: 1fr;
  }
}
</style>
