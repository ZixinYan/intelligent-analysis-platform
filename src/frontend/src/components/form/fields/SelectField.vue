<script setup lang="ts">
import { computed, watch } from 'vue'
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

const { options, loading, error } = useOptionsLoader(() => props.field, () => props.schema, () => props.model)
const multiple = computed(() => props.field.componentType === 'MULTI_SELECT' || Boolean(props.field.multiple))
const selectedValues = computed(() => Array.isArray(props.modelValue) ? props.modelValue.map(item => String(item)) : [])

watch([options, loading], ([newOptions, isLoading]) => {
  if (isLoading || !newOptions.length) return
  if (multiple.value) {
    const validValues = selectedValues.value.filter(v => newOptions.some(o => o.value === v))
    if (validValues.length !== selectedValues.value.length) {
      emit('update:modelValue', validValues)
    }
    return
  }
  const current = String(props.modelValue ?? '')
  if (current && !newOptions.some(o => o.value === current)) {
    emit('update:modelValue', '')
  }
})

function handleChange(event: Event) {
  const target = event.target as HTMLSelectElement
  if (multiple.value) {
    emit('update:modelValue', Array.from(target.selectedOptions).map(option => option.value))
    return
  }
  emit('update:modelValue', target.value)
}

const placeholder = computed(() => {
  if (loading.value) return '加载中...'
  if (error.value) return '加载失败'
  if (props.field.placeholder) return props.field.placeholder
  return '请选择'
})
</script>

<template>
  <div class="select-wrapper">
    <div class="select-container" :class="{ 'is-loading': loading, 'is-error': !!error }">
      <select
        class="field-input"
        :multiple="multiple"
        :value="multiple ? selectedValues : String(modelValue ?? '')"
        :disabled="disabled || loading"
        @change="handleChange"
      >
        <option v-if="!multiple" value="" disabled>{{ placeholder }}</option>
        <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
      <span v-if="loading" class="select-spinner" aria-hidden="true" />
    </div>
    <div v-if="error" class="select-error">{{ error }}</div>
    <div v-if="!loading && !error && options.length === 0 && field.optionsSource?.type === 'remote' && field.optionsSource?.uri && !field.optionsSource.uri.includes('{')" class="select-empty">
      暂无数据
    </div>
  </div>
</template>

<style scoped>
.select-wrapper {
  display: grid;
  gap: 6px;
}

.select-container {
  position: relative;
}

.field-input {
  width: 100%;
  border: 1px solid var(--iap-input-border);
  border-radius: 12px;
  background: var(--iap-input-bg);
  color: var(--iap-text-primary);
  padding: 10px 36px 10px 12px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2398a2b2' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
  outline: none;
}

.field-input:hover:not(:disabled) {
  border-color: var(--iap-divider-strong);
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

.is-loading .field-input {
  border-color: var(--iap-input-border-focus);
  background-image: none;
  padding-right: 40px;
}

.is-error .field-input {
  border-color: var(--iap-error-border);
}

.select-spinner {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 14px;
  height: 14px;
  border: 2px solid var(--iap-divider-strong);
  border-top-color: var(--iap-text-accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  pointer-events: none;
}

@keyframes spin {
  to { transform: translateY(-50%) rotate(360deg); }
}

.select-error {
  font-size: 12px;
  color: var(--iap-error-text);
}

.select-empty {
  font-size: 12px;
  color: var(--iap-text-tertiary);
  padding: 4px 12px;
}
</style>
