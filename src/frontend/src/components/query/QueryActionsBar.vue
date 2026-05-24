<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '@/components/icons/AppIcon.vue'
import { validateQuery, previewQuery, inferQuerySchema } from '@/api/query'
import { useAsyncTask } from '@/composables/useAsyncTask'
import { useWorkflowStore } from '@/stores/workflow'
import AiSqlDialog from '@/components/ai/AiSqlDialog.vue'
import type { DatasetDTO, QueryResultDTO, QueryRequestDTO } from '@/types/contract'
import type { WorkflowNode } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowNode
  datasourceId: string
  sqlTemplate: string
  tableName?: string
}>()

const emit = defineEmits<{
  'sql-update': [sql: string]
}>()

const workflow = useWorkflowStore()

function buildRequest(): QueryRequestDTO {
  return {
    datasourceId: props.datasourceId,
    sql: props.sqlTemplate,
    parameters: {},
    option: { limit: 100 },
  }
}

const canRun = computed(() => !!props.datasourceId && !!props.sqlTemplate.trim())
const showAiDialog = ref(false)

function onAiAccepted(sql: string) {
  emit('sql-update', sql)
  showAiDialog.value = false
}

type ValidateState = 'idle' | 'loading' | 'ok' | 'fail'

const validateState = ref<ValidateState>('idle')
const validateErrors = ref<string[]>([])

async function handleValidate() {
  if (!canRun.value) return
  validateState.value = 'loading'
  validateErrors.value = []
  try {
    const result = await validateQuery(buildRequest())
    if (result.valid) {
      validateState.value = 'ok'
    }
    else {
      validateState.value = 'fail'
      const msgs: string[] = []
      if (result.message) msgs.push(result.message)
      if (result.violationCodes?.length) msgs.push(...result.violationCodes)
      validateErrors.value = msgs.length ? msgs : ['SQL 校验失败']
    }
  }
  catch (e: unknown) {
    validateState.value = 'fail'
    validateErrors.value = [(e instanceof Error ? e.message : String(e)) || 'SQL 校验失败']
  }
}

const previewing = ref(false)
const previewResult = ref<QueryResultDTO | null>(null)
const previewError = ref<string>()

function getColumnKeys(dataset: DatasetDTO): string[] {
  if (dataset.columns?.length) {
    return dataset.columns.map(c => String(c.field ?? c.name ?? Object.values(c)[0] ?? ''))
  }
  if (dataset.rows?.length) {
    return Object.keys(dataset.rows[0])
  }
  return []
}

async function handlePreview() {
  if (!canRun.value) return
  previewing.value = true
  previewResult.value = null
  previewError.value = undefined
  try {
    const result = await previewQuery(buildRequest())
    previewResult.value = result

    try {
      const schema = await inferQuerySchema(buildRequest())
      if (schema?.fields?.length) {
        workflow.updateNodeSchema(props.node.id, schema)
        workflow.propagateSchemaFrom(props.node.id)
      }
    }
    catch {
    }
  }
  catch (e: unknown) {
    previewError.value = (e instanceof Error ? e.message : String(e)) || '预览失败'
  }
  finally {
    previewing.value = false
  }
}

const asyncTask = useAsyncTask()
const execError = ref<string>()

async function handleSubmitAsync() {
  if (!canRun.value) return
  execError.value = undefined
  try {
    await asyncTask.submit(buildRequest())
    if (asyncTask.status.value === 'FAILED') {
      execError.value = asyncTask.error.value?.message ?? '执行失败'
    }
  }
  catch (e: unknown) {
    execError.value = (e instanceof Error ? e.message : String(e)) || '执行失败'
  }
}

async function handleCancel() {
  await asyncTask.cancel()
}

const isPolling = computed(() => asyncTask.polling.value || asyncTask.loading.value)
</script>

<template>
  <div class="qab">
    <div class="qab__actions">
      <button
        class="qab__btn qab__btn--ai"
        :disabled="!datasourceId"
        @click="showAiDialog = true"
      >
        <AppIcon name="ai" :size="13" />
        AI 生成
      </button>

      <button
        class="qab__btn"
        :disabled="!canRun || validateState === 'loading'"
        @click="handleValidate"
      >
        <span v-if="validateState === 'loading'" class="qab__spin" />
        <template v-else>
          <span v-if="validateState === 'ok'" class="qab__icon qab__icon--ok"><AppIcon name="check" :size="12" /></span>
          <span v-else-if="validateState === 'fail'" class="qab__icon qab__icon--fail"><AppIcon name="x" :size="12" /></span>
        </template>
        校验 SQL
      </button>

      <button
        class="qab__btn"
        :disabled="!canRun || previewing"
        @click="handlePreview"
      >
        <span v-if="previewing" class="qab__spin" />
        预览（前 100 行）
      </button>

      <button
        v-if="isPolling"
        class="qab__btn qab__btn--cancel"
        @click="handleCancel"
      >
        取消执行
      </button>
      <button
        v-else
        class="qab__btn qab__btn--primary"
        :disabled="!canRun || asyncTask.loading.value"
        @click="handleSubmitAsync"
      >
        <span v-if="asyncTask.loading.value" class="qab__spin" />
        异步执行
      </button>
    </div>

    <div v-if="validateState === 'fail' && validateErrors.length" class="qab__errors">
      <div v-for="(err, i) in validateErrors" :key="i" class="qab__error-line">
        <span class="qab__error-icon"><AppIcon name="x" :size="12" /></span>
        <span>{{ err }}</span>
      </div>
    </div>

    <div v-if="validateState === 'ok'" class="qab__ok">
      <AppIcon name="check" :size="12" />
      <span>SQL 语法校验通过</span>
    </div>

    <div v-if="execError" class="qab__errors">
      <div class="qab__error-line">
        <span class="qab__error-icon"><AppIcon name="x" :size="12" /></span>
        <span>{{ execError }}</span>
      </div>
    </div>

    <div v-if="previewError" class="qab__errors">
      <div class="qab__error-line">
        <span class="qab__error-icon"><AppIcon name="x" :size="12" /></span>
        <span>{{ previewError }}</span>
      </div>
    </div>

    <div v-if="isPolling" class="qab__polling">
      <span class="qab__spin" />
      执行中{{ asyncTask.progress.value ? ` · ${asyncTask.progress.value}%` : '' }}
    </div>

    <div v-if="asyncTask.status.value === 'CANCELLED'" class="qab__cancelled">已取消</div>

    <div v-if="asyncTask.status.value === 'SUCCEEDED'" class="qab__success">
      <AppIcon name="check" :size="12" />
      <span>执行成功</span>
      <template v-if="asyncTask.dataset.value?.rows?.length">
        <span>· {{ asyncTask.dataset.value.rows.length }} 行</span>
      </template>
    </div>

    <template v-if="previewResult?.dataset">
      <div class="qab__preview">
        <div class="qab__preview-meta">
          <span>{{ previewResult.dataset.rows?.length ?? 0 }} 行</span>
          <span v-if="previewResult.executionMeta?.durationMs != null">
            {{ previewResult.executionMeta.durationMs }}ms
          </span>
          <span v-if="previewResult.executionMeta?.cacheHit" class="qab__preview-cached">缓存命中</span>
        </div>
        <div class="qab__preview-scroll">
          <table class="qab__table">
            <thead>
              <tr>
                <th
                  v-for="col in getColumnKeys(previewResult.dataset)"
                  :key="col"
                  class="qab__th"
                >{{ col }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(row, i) in (previewResult.dataset.rows ?? [])"
                :key="i"
                class="qab__tr"
              >
                <td
                  v-for="col in getColumnKeys(previewResult.dataset)"
                  :key="col"
                  class="qab__td"
                >{{ row[col] ?? '' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <AiSqlDialog
      v-if="showAiDialog && datasourceId"
      :datasource-id="datasourceId"
      :table-name="tableName ?? ''"
      @accept="onAiAccepted"
      @cancel="showAiDialog = false"
    />
  </div>
</template>

<style scoped>
.qab {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #1e293b;
  margin-top: 4px;
}

.qab__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.qab__btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 8px;
  border: 1px solid #334155;
  background: rgba(51, 65, 85, 0.25);
  color: #94a3b8;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  font-family: inherit;
  white-space: nowrap;
}

.qab__btn:hover:not(:disabled) {
  background: rgba(51, 65, 85, 0.5);
  color: #e2e8f0;
}

.qab__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.qab__btn--ai {
  border-color: rgba(99, 102, 241, 0.45);
  background: rgba(99, 102, 241, 0.12);
  color: #a5b4fc;
}

.qab__btn--ai:hover:not(:disabled) {
  background: rgba(99, 102, 241, 0.25);
}

.qab__btn--primary {
  border-color: rgba(59, 130, 246, 0.45);
  background: rgba(59, 130, 246, 0.12);
  color: #93c5fd;
}

.qab__btn--primary:hover:not(:disabled) {
  background: rgba(59, 130, 246, 0.25);
}

.qab__btn--cancel {
  border-color: rgba(239, 68, 68, 0.4);
  background: rgba(239, 68, 68, 0.1);
  color: #fca5a5;
}

.qab__btn--cancel:hover {
  background: rgba(239, 68, 68, 0.2);
}

.qab__icon {
  display: grid;
  place-items: center;
}

.qab__icon--ok  { color: #4ade80; }
.qab__icon--fail { color: #f87171; }

.qab__spin {
  display: inline-block;
  width: 10px;
  height: 10px;
  border: 1.5px solid rgba(255, 255, 255, 0.15);
  border-top-color: currentColor;
  border-radius: 50%;
  animation: qab-spin 0.7s linear infinite;
  flex-shrink: 0;
}

@keyframes qab-spin { to { transform: rotate(360deg); } }

.qab__ok {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #4ade80;
  padding: 6px 10px;
  background: rgba(74, 222, 128, 0.08);
  border: 1px solid rgba(74, 222, 128, 0.2);
  border-radius: 8px;
}

.qab__errors {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 8px;
}

.qab__error-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  color: #fca5a5;
  line-height: 1.5;
}

.qab__error-icon {
  display: grid;
  place-items: center;
  margin-top: 2px;
  flex-shrink: 0;
}

.qab__polling {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.qab__cancelled {
  font-size: 12px;
  color: #f59e0b;
}

.qab__success {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #4ade80;
}

.qab__preview {
  border: 1px solid #1e293b;
  border-radius: 10px;
  overflow: hidden;
}

.qab__preview-meta {
  display: flex;
  gap: 12px;
  padding: 6px 10px;
  background: #0a0f1e;
  border-bottom: 1px solid #1e293b;
  font-size: 11px;
  color: #475569;
}

.qab__preview-cached { color: #38bdf8; }

.qab__preview-scroll {
  overflow-x: auto;
  max-height: 240px;
  overflow-y: auto;
}

.qab__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.qab__th {
  padding: 6px 10px;
  text-align: left;
  font-weight: 600;
  color: #64748b;
  background: #0a0f1e;
  white-space: nowrap;
  position: sticky;
  top: 0;
  border-bottom: 1px solid #1e293b;
}

.qab__tr:nth-child(even) { background: rgba(15, 23, 42, 0.4); }

.qab__td {
  padding: 5px 10px;
  color: #94a3b8;
  white-space: nowrap;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  border-bottom: 1px solid rgba(30, 41, 59, 0.5);
  font-family: 'SFMono-Regular', ui-monospace, monospace;
}
</style>
