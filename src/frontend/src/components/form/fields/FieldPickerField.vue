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
      :value="String(modelValue ?? '')"
      :disabled="disabled"
      @change="handleSelectChange"
    >
      <option value="">请选择字段</option>
      <option v-for="candidate in props.candidates ?? []" :key="candidate" :value="candidate">{{ candidate }}</option>
    </select>
    <div v-if="props.candidates?.length" class="field-picker__hint">候选字段：{{ props.candidates.join(', ') }}</div>
  </div>
</template>

<style scoped>
.field-picker {
  display: grid;
  gap: 6px;
}
.field-picker__select {
  width: 100%;
  border: 1px solid #334155;
  border-radius: 12px;
  background: #020617;
  color: inherit;
  padding: 10px 12px;
}
.field-picker__list {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #334155;
  border-radius: 12px;
  background: #020617;
}
.field-picker__option {
  display: flex;
  align-items: center;
  gap: 8px;
}
.field-picker__hint {
  font-size: 12px;
  color: #38bdf8;
}
</style>
