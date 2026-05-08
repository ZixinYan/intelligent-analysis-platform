<script setup lang="ts">
import { computed } from 'vue'
import type { PanelFieldDTO } from '@/types/contract'

const props = defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const language = computed<string>(() => {
  const lang = props.field.props?.language
  if (typeof lang === 'string') return lang
  return 'plaintext'
})

const minLines = computed<number>(() => {
  const v = props.field.props?.minLines
  return typeof v === 'number' ? v : 10
})

const placeholderText = computed<string>(() => {
  return props.field.placeholder ?? `请输入 ${language.value} 代码`
})

const languageLabel = computed<string>(() => {
  const map: Record<string, string> = { python: 'Python 3', java: 'Java', sql: 'SQL' }
  return map[language.value] ?? language.value
})
</script>

<template>
  <div class="code-editor-wrapper">
    <div class="code-editor-header">
      <span class="lang-badge">{{ languageLabel }}</span>
    </div>
    <textarea
      class="code-editor"
      :rows="minLines"
      :placeholder="placeholderText"
      :value="String(modelValue ?? '')"
      :disabled="disabled"
      :data-language="language"
      spellcheck="false"
      autocomplete="off"
      autocorrect="off"
      autocapitalize="off"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    />
  </div>
</template>

<style scoped>
.code-editor-wrapper {
  width: 100%;
  border: 1px solid #334155;
  border-radius: 12px;
  overflow: hidden;
  background: #020617;
}

.code-editor-header {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  background: #0f172a;
  border-bottom: 1px solid #1e293b;
}

.lang-badge {
  font-size: 11px;
  font-family: 'SFMono-Regular', ui-monospace, monospace;
  color: #64748b;
  background: #1e293b;
  border-radius: 4px;
  padding: 2px 8px;
  letter-spacing: 0.04em;
}

.code-editor {
  width: 100%;
  resize: vertical;
  border: none;
  outline: none;
  background: #020617;
  color: #e2e8f0;
  padding: 12px;
  font-family: 'SFMono-Regular', ui-monospace, monospace;
  font-size: 13px;
  line-height: 1.6;
  tab-size: 4;
  box-sizing: border-box;
}

.code-editor::placeholder {
  color: #475569;
  font-style: italic;
}

.code-editor:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
