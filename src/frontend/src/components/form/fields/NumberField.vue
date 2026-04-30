<script setup lang="ts">
import type { PanelFieldDTO } from '@/types/contract'

defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
}>()

function handleInput(event: Event) {
  const nextValue = (event.target as HTMLInputElement).value
  emit('update:modelValue', nextValue === '' ? undefined : Number(nextValue))
}
</script>

<template>
  <input
    class="field-input"
    type="number"
    :value="modelValue ?? ''"
    :disabled="disabled"
    @input="handleInput"
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
</style>
