<script setup lang="ts">
import { computed } from 'vue'

interface OutputVarItem {
  name: string
  label?: string
  valueType?: string
}

const VALUE_TYPES = ['STRING', 'LONG', 'DOUBLE', 'BOOLEAN', 'JSON']

const props = defineProps<{
  field: unknown
  modelValue: unknown
  model: Record<string, unknown>
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: OutputVarItem[]]
}>()

const items = computed<OutputVarItem[]>(() => {
  if (!Array.isArray(props.modelValue)) return []
  return props.modelValue as OutputVarItem[]
})

function handleNameInput(index: number, e: Event) {
  const raw = (e.target as HTMLInputElement).value
  const name = raw.replace(/\s+/g, '_')
  emit('update:modelValue', items.value.map((item, i) =>
    i === index ? { ...item, name } : item,
  ))
}

function handleLabelInput(index: number, e: Event) {
  const label = (e.target as HTMLInputElement).value
  emit('update:modelValue', items.value.map((item, i) =>
    i === index ? { ...item, label } : item,
  ))
}

function handleTypeChange(index: number, e: Event) {
  const valueType = (e.target as HTMLSelectElement).value
  emit('update:modelValue', items.value.map((item, i) =>
    i === index ? { ...item, valueType } : item,
  ))
}

function addItem() {
  emit('update:modelValue', [...items.value, { name: '', label: '', valueType: 'STRING' }])
}

function removeItem(index: number) {
  emit('update:modelValue', items.value.filter((_, i) => i !== index))
}
</script>

<template>
  <div class="ovl">
    <div v-if="items.length > 0" class="ovl__header">
      <span class="ovl__col-label">字段名</span>
      <span class="ovl__col-label">显示名</span>
      <span class="ovl__col-label">类型</span>
    </div>
    <div v-for="(item, index) in items" :key="index" class="ovl__row">
      <input
        class="ovl__input"
        :value="item.name"
        :disabled="disabled"
        placeholder="字段名（必填）"
        @input="handleNameInput(index, $event)"
      />
      <input
        class="ovl__input"
        :value="item.label"
        :disabled="disabled"
        placeholder="显示名（可选）"
        @input="handleLabelInput(index, $event)"
      />
      <select
        class="ovl__select"
        :value="item.valueType ?? 'STRING'"
        :disabled="disabled"
        @change="handleTypeChange(index, $event)"
      >
        <option v-for="t in VALUE_TYPES" :key="t" :value="t">{{ t }}</option>
      </select>
      <button v-if="!disabled" class="ovl__remove" title="删除" @click="removeItem(index)">✕</button>
    </div>
    <button v-if="!disabled" class="ovl__add" @click="addItem">+ 添加字段</button>
    <div v-if="items.length === 0 && disabled" class="ovl__empty">未定义输出字段</div>
  </div>
</template>

<style scoped>
.ovl { display: flex; flex-direction: column; gap: 6px; }
.ovl__header { display: grid; grid-template-columns: 1fr 1fr 90px 24px; gap: 6px; padding: 0 2px; }
.ovl__col-label { font-size: 10px; font-weight: 600; color: var(--iap-text-tertiary); letter-spacing: 0.05em; text-transform: uppercase; }
.ovl__row { display: grid; grid-template-columns: 1fr 1fr 90px 24px; gap: 6px; align-items: center; }
.ovl__input { height: 30px; padding: 0 8px; border: 1px solid var(--iap-input-border); border-radius: 6px; background: var(--iap-input-bg); color: var(--iap-text-primary); font-size: 12px; outline: none; transition: border-color 0.15s, box-shadow 0.15s; min-width: 0; }
.ovl__input:focus { border-color: var(--iap-input-border-focus); box-shadow: 0 0 0 3px var(--iap-accent-ring); }
.ovl__input:disabled { opacity: 0.6; cursor: not-allowed; }
.ovl__select { height: 30px; padding: 0 6px; border: 1px solid var(--iap-input-border); border-radius: 6px; background: var(--iap-input-bg); color: var(--iap-text-primary); font-size: 12px; outline: none; cursor: pointer; transition: border-color 0.15s; }
.ovl__select:focus { border-color: var(--iap-input-border-focus); }
.ovl__select:disabled { opacity: 0.6; cursor: not-allowed; }
.ovl__remove { display: grid; place-items: center; width: 24px; height: 24px; border: none; border-radius: 5px; background: transparent; color: var(--iap-text-tertiary); font-size: 10px; cursor: pointer; transition: background 0.12s, color 0.12s; padding: 0; }
.ovl__remove:hover { background: color-mix(in srgb, var(--iap-error-text, #ef4444) 12%, transparent); color: var(--iap-error-text, #ef4444); }
.ovl__add { align-self: flex-start; padding: 5px 12px; border: 1px dashed var(--iap-divider); border-radius: 6px; background: transparent; color: var(--iap-text-secondary); font-size: 12px; cursor: pointer; transition: background 0.12s, border-color 0.12s, color 0.12s; }
.ovl__add:hover { background: var(--iap-hover-bg); border-color: var(--iap-text-accent); color: var(--iap-text-accent); }
.ovl__empty { font-size: 12px; color: var(--iap-text-disabled); font-style: italic; }
</style>
