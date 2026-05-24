<script setup lang="ts">
import { computed } from 'vue'
import type { PanelFieldDTO } from '@/types/contract'

const props = defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
  candidates?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | string[]]
}>()

const multiple = computed(() => props.field.componentType === 'FIELD_MULTI_SELECTOR' || Boolean(props.field.multiple))
const selectedValues = computed(() => Array.isArray(props.modelValue) ? props.modelValue.map(item => String(item)) : [])
const singleValue = computed(() => String(props.modelValue ?? ''))
const hasCandidates = computed(() => Boolean(props.candidates?.length))

function handleSelectChange(event: Event) {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', target.value)
}

function toggleCandidate(candidate: string, checked: boolean) {
  const current = new Set(selectedValues.value)
  if (checked) {
    current.add(candidate)
  }
  else {
    current.delete(candidate)
  }
  emit('update:modelValue', [...current])
}
</script>

<template>
  <div class="field-picker">
    <div v-if="multiple" class="field-picker__list">
      <label v-for="candidate in props.candidates ?? []" :key="candidate" class="field-picker__option">
        <input
          type="checkbox"
          :checked="selectedValues.includes(candidate)"
          :disabled="disabled"
          @change="toggleCandidate(candidate, ($event.target as HTMLInputElement).checked)"
        />
        <span>{{ candidate }}</span>
      </label>
    </div>
    <select
      v-else
      class="field-picker__select"
      :class="{ 'field-picker__select--placeholder': !singleValue }"
      :value="singleValue"
      :disabled="disabled"
      @change="handleSelectChange"
    >
      <option value="" disabled hidden>请选择字段</option>
      <option v-for="candidate in props.candidates ?? []" :key="candidate" :value="candidate">{{ candidate }}</option>
    </select>
    <div v-if="hasCandidates" class="field-picker__hint">候选字段：{{ (props.candidates ?? []).join('、') }}</div>
  </div>
</template>

<style scoped>
.field-picker {
  display: grid;
  gap: 8px;
}

.field-picker__select {
  width: 100%;
  min-height: 44px;
  border: 1px solid var(--iap-input-border);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-input-bg-focus) 100%);
  color: var(--iap-text-primary);
  padding: 11px 44px 11px 14px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-input-bg-focus) 100%);
  background-repeat: no-repeat, no-repeat;
  background-position: right 14px center, center;
  background-size: 14px 14px, auto;
  box-shadow: var(--iap-select-shadow);
  outline: none;
}

.field-picker__select:hover:not(:disabled) {
  border-color: var(--iap-divider-strong);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-hover) 100%);
  transform: translateY(-1px);
}

.field-picker__select:focus {
  border-color: var(--iap-input-border-focus);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%23155aef' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-focus) 100%);
  box-shadow: var(--iap-select-shadow), var(--iap-select-shadow-focus);
}

.field-picker__select:disabled {
  opacity: 0.62;
  cursor: not-allowed;
  transform: none;
}

.field-picker__select--placeholder {
  color: var(--iap-text-placeholder);
}

.field-picker__list {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--iap-card-border);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-card-bg) 100%);
  box-shadow: var(--iap-shadow-panel);
}

.field-picker__option {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--iap-text-secondary);
}

.field-picker__option input {
  accent-color: var(--iap-text-accent);
}

.field-picker__hint {
  font-size: 12px;
  color: var(--iap-text-tertiary);
}
</style>
