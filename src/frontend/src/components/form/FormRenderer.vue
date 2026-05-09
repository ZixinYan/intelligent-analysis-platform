<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import FieldRenderer from './FieldRenderer.vue'
import { useFieldState, validateFieldValue } from '@/composables/useFormValidation'
import type { FieldCandidateSlotDTO, NodeConfigSchemaDTO, PanelFieldDTO, PanelSectionDTO } from '@/types/contract'

const props = defineProps<{
  schema?: NodeConfigSchemaDTO
  modelValue: Record<string, unknown>
  candidateSlots?: FieldCandidateSlotDTO[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
  valid: [value: boolean]
}>()

const draft = reactive<Record<string, unknown>>({ ...props.modelValue })
const errors = reactive<Record<string, string[]>>({})

watch(() => props.modelValue, (value) => {
  Object.keys(draft).forEach(key => delete draft[key])
  Object.assign(draft, value)
  draft.__schema = props.modelValue.__schema ?? props.schema
}, { deep: true, immediate: true })

const orderedSections = computed<PanelSectionDTO[]>(() => {
  return [...(props.schema?.sections ?? [])]
    .sort((a, b) => a.order - b.order)
    .map(section => ({
      ...section,
      fields: [...section.fields].sort((a, b) => (a.order ?? 0) - (b.order ?? 0)),
    }))
})

const activeFields = computed(() => {
  return orderedSections.value.flatMap((section) => {
    return section.fields.filter((field) => {
      const { visible } = useFieldState(field, () => draft)
      return visible.value
    })
  })
})

watch([orderedSections, () => ({ ...draft })], () => {
  Object.keys(errors).forEach((key) => {
    delete errors[key]
  })
  for (const field of activeFields.value) {
    errors[field.field] = validateFieldValue(field, draft[field.field])
  }
  emit('valid', !Object.values(errors).some(item => item.length > 0))
}, { immediate: true, deep: true })

function fieldCandidates(field: PanelFieldDTO) {
  if (field.componentType !== 'FIELD_PICKER' && field.componentType !== 'FIELD_MULTI_SELECTOR') {
    return undefined
  }
  return props.candidateSlots
    ?.find(item => item.slot === field.field)
    ?.candidates
    .map(item => item.field)
}

function updateField(field: PanelFieldDTO, value: unknown) {
  draft[field.field] = value
  emit('update:modelValue', { ...draft })
}
</script>

<template>
  <div class="form-renderer">
    <section v-for="section in orderedSections" :key="section.key" class="form-renderer__section">
      <header class="form-renderer__header">{{ section.title }}</header>
      <FieldRenderer
        v-for="field in section.fields"
        :key="field.field"
        :field="field"
        :model-value="draft[field.field]"
        :model="draft"
        :candidates="fieldCandidates(field)"
        :error="errors[field.field]?.[0]"
        @update:model-value="updateField(field, $event)"
      />
    </section>
  </div>
</template>

<style scoped>
.form-renderer {
  display: grid;
  gap: 12px;
}
.form-renderer__section {
  padding: 14px;
  border: 1px solid #1e293b;
  border-radius: 14px;
  background: #0a0f1e;
  display: grid;
  gap: 14px;
}
.form-renderer__header {
  font-weight: 600;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #475569;
  padding-bottom: 10px;
  border-bottom: 1px solid #1e293b;
}
</style>
