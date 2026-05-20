<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { PanelFieldDTO, FieldSchemaDTO } from '@/types/contract'
import AiSqlDialog from '@/components/ai/AiSqlDialog.vue'
import { getTableSchema } from '@/api/datasource'

const props = defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
  model?: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const showAiDialog = ref(false)
const tableSchema = ref<FieldSchemaDTO[]>([])
const schemaLoading = ref(false)
const schemaExpanded = ref(true)

const currentTable = computed(() => {
  const tableId = props.model?.tableId
  return typeof tableId === 'string' && tableId ? tableId : null
})

const currentDatasourceId = computed(() => {
  const ds = props.model?.datasourceId
  return typeof ds === 'string' && ds ? ds : null
})

watch([currentDatasourceId, currentTable], async ([dsId, tableName]) => {
  tableSchema.value = []
  if (!dsId || !tableName) return
  schemaLoading.value = true
  try {
    tableSchema.value = await getTableSchema(dsId, tableName)
  } catch {
    tableSchema.value = []
  } finally {
    schemaLoading.value = false
  }
}, { immediate: true })

function handleInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}

function insertTableRef() {
  if (!currentTable.value) return
  const current = String(props.modelValue ?? '').trim()
  const template = `SELECT *\nFROM ${currentTable.value}\nLIMIT 100`
  emit('update:modelValue', current ? `${current}\n\n/* table ref: ${currentTable.value} */` : template)
}

function onAiSqlAccepted(sql: string) {
  emit('update:modelValue', sql)
  showAiDialog.value = false
}
</script>

<template>
  <div class="sql-editor-wrapper">
    <div v-if="currentTable" class="sql-table-hint">
      <span class="sql-table-hint__icon">⌗</span>
      <span class="sql-table-hint__label">当前表：<strong>{{ currentTable }}</strong></span>
      <button
        type="button"
        class="sql-table-hint__btn"
        :disabled="disabled"
        @click="insertTableRef"
      >插入示例 SQL</button>
      <button
        v-if="currentDatasourceId"
        type="button"
        class="sql-table-hint__btn sql-table-hint__btn--ai"
        :disabled="disabled"
        @click="showAiDialog = true"
      >✦ AI 生成</button>
    </div>

    <div v-if="currentTable" class="sql-schema-panel">
      <button type="button" class="sql-schema-panel__toggle" @click="schemaExpanded = !schemaExpanded">
        <span class="sql-schema-panel__arrow" :class="{ 'sql-schema-panel__arrow--open': schemaExpanded }">▶</span>
        <span>字段列表</span>
        <span v-if="!schemaLoading" class="sql-schema-panel__count">{{ tableSchema.length }} 列</span>
        <span v-else class="sql-schema-panel__count">加载中…</span>
      </button>
      <ul v-if="schemaExpanded && tableSchema.length > 0" class="sql-schema-panel__list">
        <li v-for="field in tableSchema" :key="field.name ?? field.fieldId" class="sql-schema-panel__field">
          <span class="sql-schema-panel__fname">{{ field.name }}</span>
          <span class="sql-schema-panel__ftype">{{ field.valueType }}</span>
        </li>
      </ul>
      <div v-else-if="schemaExpanded && !schemaLoading && tableSchema.length === 0" class="sql-schema-panel__empty">暂无字段信息</div>
    </div>

    <textarea
      class="sql-editor"
      rows="8"
      :placeholder="field.placeholder ?? (currentTable ? `SELECT * FROM ${currentTable} WHERE ...` : '请输入 SQL')"
      :value="String(modelValue ?? '')"
      :disabled="disabled"
      spellcheck="false"
      @input="handleInput"
    />

    <AiSqlDialog
      v-if="showAiDialog && currentDatasourceId && currentTable"
      :datasourceId="currentDatasourceId"
      :tableName="currentTable"
      @accept="onAiSqlAccepted"
      @cancel="showAiDialog = false"
    />
  </div>
</template>

<style scoped>
.sql-editor-wrapper {
  display: grid;
  gap: 8px;
}

.sql-table-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.25);
  border-radius: 10px;
  font-size: 13px;
  color: #93c5fd;
}

.sql-table-hint__icon {
  font-size: 15px;
  color: #3b82f6;
}

.sql-table-hint__label {
  flex: 1;
}

.sql-table-hint__label strong {
  color: #bfdbfe;
  font-family: 'SFMono-Regular', ui-monospace, monospace;
}

.sql-table-hint__btn {
  background: rgba(37, 99, 235, 0.2);
  border: 1px solid rgba(37, 99, 235, 0.4);
  border-radius: 7px;
  color: #93c5fd;
  font-size: 12px;
  padding: 3px 10px;
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}

.sql-table-hint__btn:hover:not(:disabled) {
  background: rgba(37, 99, 235, 0.35);
}

.sql-table-hint__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.sql-table-hint__btn--ai {
  background: rgba(99, 102, 241, 0.2);
  border-color: rgba(99, 102, 241, 0.4);
  color: #a5b4fc;
}

.sql-table-hint__btn--ai:hover:not(:disabled) {
  background: rgba(99, 102, 241, 0.35);
}

.sql-editor {
  width: 100%;
  resize: vertical;
  border: 1px solid #334155;
  border-radius: 12px;
  background: #020617;
  color: #e2e8f0;
  padding: 12px;
  font-family: 'SFMono-Regular', ui-monospace, monospace;
  font-size: 13px;
  line-height: 1.6;
  min-height: 140px;
  transition: border-color 0.15s;
}

.sql-editor:focus {
  outline: none;
  border-color: #334155;
  box-shadow: 0 0 0 3px rgba(51, 65, 85, 0.4);
}

.sql-editor::placeholder {
  color: #475569;
}

.sql-schema-panel {
  border: 1px solid rgba(51, 65, 85, 0.6);
  border-radius: 10px;
  overflow: hidden;
  background: #0a0f1e;
}

.sql-schema-panel__toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 10px;
  background: transparent;
  border: none;
  color: #64748b;
  font-size: 11px;
  cursor: pointer;
  text-align: left;
  transition: color 0.15s;
}

.sql-schema-panel__toggle:hover {
  color: #94a3b8;
}

.sql-schema-panel__arrow {
  font-size: 9px;
  transition: transform 0.15s;
  display: inline-block;
}

.sql-schema-panel__arrow--open {
  transform: rotate(90deg);
}

.sql-schema-panel__count {
  margin-left: auto;
  color: #475569;
}

.sql-schema-panel__list {
  margin: 0;
  padding: 0 0 6px;
  list-style: none;
  max-height: 160px;
  overflow-y: auto;
}

.sql-schema-panel__field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 3px 10px;
  gap: 8px;
}

.sql-schema-panel__field:hover {
  background: rgba(51, 65, 85, 0.3);
}

.sql-schema-panel__fname {
  font-family: 'SFMono-Regular', ui-monospace, monospace;
  font-size: 12px;
  color: #cbd5e1;
}

.sql-schema-panel__ftype {
  font-size: 11px;
  color: #475569;
  font-family: 'SFMono-Regular', ui-monospace, monospace;
  flex-shrink: 0;
}

.sql-schema-panel__empty {
  padding: 6px 10px 8px;
  font-size: 11px;
  color: #475569;
}
</style>
