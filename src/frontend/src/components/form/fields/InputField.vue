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
    v-if="field.componentType === 'TEXTAREA'"
    class="field-input field-input--textarea"
    rows="4"
    :placeholder="field.placeholder ?? '请输入'"
    :value="String(modelValue ?? '')"
    :disabled="disabled"
    @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
  />
  <input
    v-else
    class="field-input"
    :placeholder="field.placeholder ?? '请输入'"
    :value="String(modelValue ?? '')"
    :disabled="disabled"
    @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  />
</template>

<style scoped>
.field-input {
  width: 100%;
  border: 1px solid #334155;
  border-radius: 12px;
  background: #020617;
  color: inherit;
  padding: 10px 12px;
}
.field-input--textarea {
  resize: vertical;
  min-height: 96px;
}
</style>
