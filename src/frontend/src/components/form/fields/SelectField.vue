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
const singleValue = computed(() => String(props.modelValue ?? ''))
const isPlaceholderVisible = computed(() => !multiple.value && !singleValue.value)

watch([options, loading], ([newOptions, isLoading]) => {
  if (isLoading || !newOptions.length) return
  if (multiple.value) {
    const validValues = selectedValues.value.filter(v => newOptions.some(o => o.value === v))
    if (validValues.length !== selectedValues.value.length) {
      emit('update:modelValue', validValues)
    }
    return
  }
  if (singleValue.value && !newOptions.some(o => o.value === singleValue.value)) {
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
        :class="{ 'field-input--placeholder': isPlaceholderVisible, 'field-input--multiple': multiple }"
        :multiple="multiple"
        :value="multiple ? selectedValues : singleValue"
        :disabled="disabled || loading"
        @change="handleChange"
      >
        <option v-if="!multiple" value="" disabled hidden>{{ placeholder }}</option>
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
  gap: 8px;
}

.select-container {
  position: relative;
}

.field-input {
  width: 100%;
  min-height: 44px;
  border: 1px solid var(--iap-input-border);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94) 0%, var(--iap-input-bg-focus) 100%);
  color: var(--iap-text-primary);
  padding: 11px 44px 11px 14px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, rgba(255, 255, 255, 0.94) 0%, var(--iap-input-bg-focus) 100%);
  background-repeat: no-repeat, no-repeat;
  background-position: right 14px center, center;
  background-size: 14px 14px, auto;
  box-shadow: var(--iap-select-shadow);
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease, transform 0.15s ease;
  outline: none;
}

.field-input:hover:not(:disabled) {
  border-color: var(--iap-divider-strong);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-hover) 100%);
  transform: translateY(-1px);
}

.field-input:focus {
  border-color: var(--iap-input-border-focus);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%23155aef' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-focus) 100%);
  box-shadow: var(--iap-select-shadow), var(--iap-select-shadow-focus);
}

.field-input--placeholder {
  color: var(--iap-text-placeholder);
}

.field-input--multiple {
  min-height: 124px;
  padding-right: 14px;
  background-image: none;
}

.field-input--multiple:hover:not(:disabled),
.field-input--multiple:focus {
  transform: none;
}

.field-input:disabled {
  opacity: 0.62;
  cursor: not-allowed;
  transform: none;
}

.is-loading .field-input {
  padding-right: 44px;
  background-image: linear-gradient(180deg, rgba(255, 255, 255, 0.94) 0%, var(--iap-input-bg-focus) 100%);
}

.is-error .field-input {
  border-color: var(--iap-error-border);
}

.select-spinner {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
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
  padding: 4px 14px;
}
</style>
