<script setup lang="ts">
import { computed } from 'vue'
import type { PanelFieldDTO } from '@/types/contract'

const props = defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
  model?: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const currentTable = computed(() => {
  const tableId = props.model?.tableId
  return typeof tableId === 'string' && tableId ? tableId : null
})

function handleInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}

function insertTableRef() {
  if (!currentTable.value) return
  const current = String(props.modelValue ?? '').trim()
  const template = `SELECT *\nFROM ${currentTable.value}\nLIMIT 100`
  emit('update:modelValue', current ? `${current}\n\n/* table ref: ${currentTable.value} */` : template)
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
</style>
