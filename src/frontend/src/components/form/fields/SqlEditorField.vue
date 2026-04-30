<script setup lang="ts">
import type { PanelFieldDTO } from '@/types/contract'

defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <textarea
    class="sql-editor"
    rows="8"
    :placeholder="field.placeholder ?? '请输入 SQL'"
    :value="String(modelValue ?? '')"
    :disabled="disabled"
    @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
  />
</template>

<style scoped>
.sql-editor {
  width: 100%;
  resize: vertical;
  border: 1px solid #334155;
  border-radius: 12px;
  background: #020617;
  color: #e2e8f0;
  padding: 12px;
  font-family: 'SFMono-Regular', ui-monospace, monospace;
}
</style>
