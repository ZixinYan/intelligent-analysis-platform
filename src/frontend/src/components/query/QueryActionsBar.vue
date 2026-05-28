<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '@/components/icons/AppIcon.vue'
import { validateQuery } from '@/api/query'
import AiSqlDialog from '@/components/ai/AiSqlDialog.vue'
import type { QueryRequestDTO } from '@/types/contract'
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
</style>
