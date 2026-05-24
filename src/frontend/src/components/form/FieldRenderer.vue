<script setup lang="ts">
import { computed } from 'vue'
import type { PanelFieldDTO } from '@/types/contract'
import { resolveFieldComponent } from '@/utils/component-map'
import { useFieldState } from '@/composables/useFormValidation'

const props = defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  model: Record<string, unknown>
  candidates?: string[]
  error?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: unknown]
}>()

const component = computed(() => resolveFieldComponent(props.field.componentType ?? 'INPUT'))
const { visible, disabled } = useFieldState(props.field, () => props.model)
</script>

<template>
  <div v-if="visible" class="field-renderer">
    <label class="field-renderer__label">
      <span>{{ field.label }}</span>
      <span v-if="field.required" class="field-renderer__required">*</span>
    </label>
    <component
      :is="component"
      :field="field"
      :model-value="modelValue"
      :model="model"
      :disabled="disabled"
      :candidates="candidates"
      :schema="model.__schema"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <div v-if="field.description" class="field-renderer__description">{{ field.description }}</div>
    <div v-if="error" class="field-renderer__error">{{ error }}</div>
  </div>
</template>

<style scoped>
.field-renderer {
  display: grid;
  gap: 8px;
}
.field-renderer__label {
  font-size: 13px;
  color: var(--iap-text-primary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.field-renderer__required {
  color: var(--iap-error-text);
}
.field-renderer__description {
  font-size: 12px;
  color: var(--iap-text-tertiary);
}
.field-renderer__error {
  font-size: 12px;
  color: var(--iap-error-text);
}
</style>
