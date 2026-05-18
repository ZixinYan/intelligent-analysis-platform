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
  border: 1px solid var(--iap-input-border);
  border-radius: 12px;
  background: var(--iap-input-bg);
  color: var(--iap-text-primary);
  padding: 10px 12px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.field-input:hover:not(:disabled) {
  background: var(--iap-input-bg-hover);
}
.field-input:focus {
  border-color: var(--iap-input-border-focus);
  background: var(--iap-input-bg-focus);
  box-shadow: 0 0 0 3px var(--iap-accent-ring);
}
.field-input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.field-input--textarea {
  resize: vertical;
  min-height: 96px;
}
</style>
