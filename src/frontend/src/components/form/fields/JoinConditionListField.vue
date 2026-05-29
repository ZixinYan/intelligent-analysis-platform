<script setup lang="ts">
import { computed } from 'vue'
import type { PanelFieldDTO } from '@/types/contract'

interface JoinCondition {
  leftField: string
  rightField: string
}

const props = defineProps<{
  field?: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
  leftFields?: string[]
  rightFields?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: JoinCondition[]]
}>()

const conditions = computed((): JoinCondition[] => {
  if (!Array.isArray(props.modelValue)) return []
  return props.modelValue.map((item) => {
    if (item && typeof item === 'object') {
      const c = item as Record<string, unknown>
      return { leftField: String(c.leftField ?? ''), rightField: String(c.rightField ?? '') }
    }
    return { leftField: '', rightField: '' }
  })
})

function addCondition() {
  emit('update:modelValue', [...conditions.value, { leftField: '', rightField: '' }])
}

function removeCondition(index: number) {
  emit('update:modelValue', conditions.value.filter((_, i) => i !== index))
}

function updateField(index: number, key: 'leftField' | 'rightField', value: string) {
  emit('update:modelValue', conditions.value.map((c, i) => i === index ? { ...c, [key]: value } : c))
}
</script>

<template>
  <div class="jcl">
    <div v-if="conditions.length === 0" class="jcl__empty">暂无条件，点击下方按钮添加</div>
    <div v-for="(cond, idx) in conditions" :key="idx" class="jcl__row">
      <div class="jcl__cell">
        <select
          class="jcl__select"
          :value="cond.leftField"
          :disabled="disabled"
          @change="updateField(idx, 'leftField', ($event.target as HTMLSelectElement).value)"
        >
          <option value="" disabled hidden>左表字段</option>
          <option v-for="f in (leftFields ?? [])" :key="f" :value="f">{{ f }}</option>
        </select>
      </div>
      <span class="jcl__eq">=</span>
      <div class="jcl__cell">
        <select
          class="jcl__select"
          :value="cond.rightField"
          :disabled="disabled"
          @change="updateField(idx, 'rightField', ($event.target as HTMLSelectElement).value)"
        >
          <option value="" disabled hidden>右表字段</option>
          <option v-for="f in (rightFields ?? [])" :key="f" :value="f">{{ f }}</option>
        </select>
      </div>
      <button class="jcl__remove" :disabled="disabled" title="移除此条件" @click="removeCondition(idx)">×</button>
    </div>
    <button class="jcl__add" :disabled="disabled" @click="addCondition">+ 添加条件</button>
  </div>
</template>

<style scoped>
.jcl {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.jcl__empty {
  font-size: 12px;
  color: var(--iap-text-placeholder);
  padding: 6px 0;
}
.jcl__row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.jcl__cell {
  flex: 1;
  min-width: 0;
}
.jcl__select {
  width: 100%;
  height: 36px;
  border: 1px solid var(--iap-input-border);
  border-radius: 8px;
  background: var(--iap-input-bg);
  color: var(--iap-text-primary);
  padding: 0 10px;
  font-size: 12px;
  outline: none;
  appearance: none;
  cursor: pointer;
}
.jcl__select:focus {
  border-color: var(--iap-input-border-focus);
  box-shadow: 0 0 0 3px var(--iap-accent-ring);
}
.jcl__select:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.jcl__eq {
  font-size: 13px;
  font-weight: 600;
  color: var(--iap-text-tertiary);
  flex-shrink: 0;
}
.jcl__remove {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--iap-text-tertiary);
  cursor: pointer;
  border-radius: 4px;
  font-size: 16px;
  line-height: 1;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  padding: 0;
}
.jcl__remove:hover:not(:disabled) {
  color: var(--iap-error-text);
  background: color-mix(in srgb, var(--iap-error-text) 10%, transparent);
}
.jcl__remove:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.jcl__add {
  align-self: flex-start;
  padding: 5px 12px;
  border: 1px dashed var(--iap-divider-strong);
  border-radius: 8px;
  background: transparent;
  color: var(--iap-text-accent);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.12s;
}
.jcl__add:hover:not(:disabled) {
  background: color-mix(in srgb, var(--iap-text-accent) 8%, transparent);
}
.jcl__add:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
