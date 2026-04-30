<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { PanelFieldDTO } from '@/types/contract'

const props = defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const bindingPrefix = '${'
const bindingSuffix = '}'
const isBinding = ref(false)
const inputValue = ref('')

const allowLiteral = computed(() => props.field.variableBinding?.allowLiteral !== false)

watch(() => props.modelValue, (value) => {
  const normalized = String(value ?? '')
  const binding = normalized.startsWith(bindingPrefix) && normalized.endsWith(bindingSuffix)
  isBinding.value = binding
  inputValue.value = binding ? normalized.slice(bindingPrefix.length, -bindingSuffix.length) : normalized
}, { immediate: true })

function emitValue(value: string) {
  emit('update:modelValue', isBinding.value ? `${bindingPrefix}${value}${bindingSuffix}` : value)
}

function toggleMode(nextBinding: boolean) {
  if (nextBinding === isBinding.value) {
    return
  }
  if (!allowLiteral.value && !nextBinding) {
    return
  }
  isBinding.value = nextBinding
  emitValue(inputValue.value)
}
</script>

<template>
  <div class="variable-binding-field">
    <div class="variable-binding-field__toolbar">
      <button type="button" class="variable-binding-field__mode" :class="{ 'is-active': !isBinding }" :disabled="disabled || !allowLiteral" @click="toggleMode(false)">
        文本
      </button>
      <button type="button" class="variable-binding-field__mode" :class="{ 'is-active': isBinding }" :disabled="disabled || !field.variableBinding?.enabled" @click="toggleMode(true)">
        变量
      </button>
    </div>
    <input
      class="variable-binding-field__input"
      :placeholder="isBinding ? (field.variableBinding?.bindingPathHint ?? '请输入变量路径') : (field.placeholder ?? '请输入')"
      :value="inputValue"
      :disabled="disabled"
      @input="emitValue(($event.target as HTMLInputElement).value)"
    />
  </div>
</template>

<style scoped>
.variable-binding-field {
  display: grid;
  gap: 8px;
}
.variable-binding-field__toolbar {
  display: inline-flex;
  gap: 8px;
}
.variable-binding-field__mode {
  border: 1px solid #334155;
  border-radius: 999px;
  background: #020617;
  color: #94a3b8;
  padding: 6px 10px;
}
.variable-binding-field__mode.is-active {
  color: #e2e8f0;
  border-color: #38bdf8;
}
.variable-binding-field__input {
  width: 100%;
  border: 1px solid #334155;
  border-radius: 12px;
  background: #020617;
  color: inherit;
  padding: 10px 12px;
}
</style>
