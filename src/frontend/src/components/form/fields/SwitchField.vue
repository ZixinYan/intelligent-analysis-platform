<script setup lang="ts">
import type { PanelFieldDTO } from '@/types/contract'

defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>

<template>
  <label class="switch-field">
    <input
      type="checkbox"
      :checked="Boolean(modelValue)"
      :disabled="disabled"
      @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
    />
    <span>{{ Boolean(modelValue) ? '开启' : '关闭' }}</span>
  </label>
</template>

<style scoped>
.switch-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #cbd5e1;
}
</style>
