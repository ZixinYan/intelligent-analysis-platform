<script setup lang="ts">
import { computed } from 'vue'
import type { PanelFieldDTO, SchemaInferResultDTO } from '@/types/contract'
import { useOptionsLoader } from '@/composables/useOptionsLoader'

const props = defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
  schema?: SchemaInferResultDTO
  model?: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | string[]]
}>()

const { options, loading } = useOptionsLoader(() => props.field, () => props.schema, () => props.model)
const multiple = computed(() => props.field.componentType === 'MULTI_SELECT' || Boolean(props.field.multiple))
const selectedValues = computed(() => Array.isArray(props.modelValue) ? props.modelValue.map(item => String(item)) : [])

function handleChange(event: Event) {
  const target = event.target as HTMLSelectElement
  if (multiple.value) {
    emit('update:modelValue', Array.from(target.selectedOptions).map(option => option.value))
    return
  }
  emit('update:modelValue', target.value)
}
</script>

<template>
  <select
    class="field-input"
    :multiple="multiple"
    :value="multiple ? selectedValues : String(modelValue ?? '')"
    :disabled="disabled"
    @change="handleChange"
  >
    <option v-if="!multiple" value="">请选择</option>
    <option v-if="loading" value="" disabled>加载中...</option>
    <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
  </select>
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
