<script setup lang="ts">
import { ref, computed } from 'vue'
import { generateSqlStream } from '@/api/ai'

const props = defineProps<{
  datasourceId: string
  tableName?: string
}>()

const emit = defineEmits<{
  accept: [sql: string]
  cancel: []
}>()

const description = ref('')
const generatedSql = ref('')
const isStreaming = ref(false)
const errorMessage = ref('')
let abortController: AbortController | null = null

async function generate() {
  if (!description.value.trim() || isStreaming.value) return
  errorMessage.value = ''
  generatedSql.value = ''
  isStreaming.value = true

  abortController = new AbortController()
  try {
    for await (const token of generateSqlStream(
      {
        datasourceId: props.datasourceId,
        tableName: props.tableName ?? '',
        description: description.value.trim(),
      },
      abortController.signal,
    )) {
      generatedSql.value += token
    }
  } catch (err) {
    if ((err as Error).name !== 'AbortError') {
      errorMessage.value = (err as Error).message || 'AI 生成失败，请重试'
    }
  } finally {
    isStreaming.value = false
    abortController = null
  }
}

function retry() {
  generate()
}

function cancel() {
  abortController?.abort()
  emit('cancel')
}

function accept() {
  if (generatedSql.value) {
    emit('accept', generatedSql.value)
  }
}

const canAccept = computed(() => generatedSql.value.trim().length > 0 && !isStreaming.value)
</script>

<template>
  <div class="ai-sql-dialog" role="dialog" aria-modal="true" aria-label="AI 生成 SQL">
    <div class="ai-sql-dialog__backdrop" @click="cancel" />
    <div class="ai-sql-dialog__panel">
      <header class="ai-sql-dialog__header">
        <span class="ai-sql-dialog__icon">✦</span>
        <span>AI 生成 SQL</span>
        <button class="ai-sql-dialog__close" @click="cancel" aria-label="关闭">✕</button>
      </header>

      <div class="ai-sql-dialog__body">
        <div v-if="tableName" class="ai-sql-dialog__meta">
          <span>数据表：<strong>{{ tableName }}</strong></span>
        </div>

        <textarea
          v-model="description"
          class="ai-sql-dialog__input"
          rows="3"
          placeholder="描述你想查询的内容，例如：统计过去 7 天各渠道新增用户数"
          :disabled="isStreaming"
          @keydown.ctrl.enter="generate"
        />

        <div v-if="errorMessage" class="ai-sql-dialog__error">{{ errorMessage }}</div>

        <div class="ai-sql-dialog__preview" v-if="generatedSql || isStreaming">
          <div class="ai-sql-dialog__preview-label">
            生成结果
            <span v-if="isStreaming" class="ai-sql-dialog__cursor">▌</span>
          </div>
          <pre class="ai-sql-dialog__sql">{{ generatedSql }}<span v-if="isStreaming" class="ai-sql-dialog__cursor"> ▌</span></pre>
        </div>
      </div>

      <footer class="ai-sql-dialog__footer">
        <button class="ai-sql-dialog__btn ai-sql-dialog__btn--primary"
                @click="generate"
                :disabled="!description.trim() || isStreaming">
          {{ isStreaming ? '生成中...' : '生成' }}
        </button>
        <button class="ai-sql-dialog__btn"
                @click="retry"
                :disabled="isStreaming || !generatedSql">
          重新生成
        </button>
        <button class="ai-sql-dialog__btn ai-sql-dialog__btn--accept"
                @click="accept"
                :disabled="!canAccept">
          使用此 SQL
        </button>
        <button class="ai-sql-dialog__btn ai-sql-dialog__btn--cancel" @click="cancel">取消</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.ai-sql-dialog {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ai-sql-dialog__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
}
.ai-sql-dialog__panel {
  position: relative;
  width: min(640px, 94vw);
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 16px;
  display: grid;
  overflow: hidden;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.5);
}
.ai-sql-dialog__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  border-bottom: 1px solid #1e293b;
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}
.ai-sql-dialog__icon {
  color: #818cf8;
  font-size: 16px;
}
.ai-sql-dialog__close {
  margin-left: auto;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 6px;
  border-radius: 6px;
  transition: color 0.15s;
}
.ai-sql-dialog__close:hover { color: #e2e8f0; }
.ai-sql-dialog__body {
  padding: 16px 18px;
  display: grid;
  gap: 12px;
}
.ai-sql-dialog__meta {
  font-size: 12px;
  color: #64748b;
}
.ai-sql-dialog__meta strong { color: #93c5fd; }
.ai-sql-dialog__input {
  width: 100%;
  background: #020617;
  border: 1px solid #334155;
  border-radius: 10px;
  color: #e2e8f0;
  padding: 10px 12px;
  font-size: 13px;
  resize: vertical;
  line-height: 1.5;
  font-family: inherit;
}
.ai-sql-dialog__input:focus { outline: none; border-color: #818cf8; }
.ai-sql-dialog__error {
  color: #f87171;
  font-size: 12px;
  padding: 8px 12px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 8px;
}
.ai-sql-dialog__preview {
  display: grid;
  gap: 6px;
}
.ai-sql-dialog__preview-label {
  font-size: 12px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;
}
.ai-sql-dialog__sql {
  background: #020617;
  border: 1px solid #1e293b;
  border-radius: 10px;
  padding: 12px;
  font-family: 'SFMono-Regular', ui-monospace, monospace;
  font-size: 12px;
  color: #93c5fd;
  white-space: pre-wrap;
  overflow-x: auto;
  min-height: 64px;
  max-height: 240px;
  overflow-y: auto;
}
.ai-sql-dialog__cursor {
  animation: blink 1s step-end infinite;
  color: #818cf8;
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
.ai-sql-dialog__footer {
  display: flex;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid #1e293b;
  flex-wrap: wrap;
}
.ai-sql-dialog__btn {
  padding: 7px 14px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid #334155;
  background: rgba(51, 65, 85, 0.3);
  color: #cbd5e1;
  transition: background 0.15s;
}
.ai-sql-dialog__btn:hover:not(:disabled) { background: rgba(51, 65, 85, 0.6); }
.ai-sql-dialog__btn:disabled { opacity: 0.45; cursor: not-allowed; }
.ai-sql-dialog__btn--primary {
  background: rgba(99, 102, 241, 0.25);
  border-color: rgba(99, 102, 241, 0.5);
  color: #a5b4fc;
}
.ai-sql-dialog__btn--primary:hover:not(:disabled) { background: rgba(99, 102, 241, 0.4); }
.ai-sql-dialog__btn--accept {
  background: rgba(34, 197, 94, 0.15);
  border-color: rgba(34, 197, 94, 0.4);
  color: #86efac;
}
.ai-sql-dialog__btn--accept:hover:not(:disabled) { background: rgba(34, 197, 94, 0.3); }
.ai-sql-dialog__btn--cancel { margin-left: auto; }
</style>
