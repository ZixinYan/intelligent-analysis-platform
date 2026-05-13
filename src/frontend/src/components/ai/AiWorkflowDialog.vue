<script setup lang="ts">
import { ref } from 'vue'
import { buildWorkflowDraft } from '@/api/ai'
import type { WorkflowDefinitionDTO } from '@/types/contract'

const props = defineProps<{
  datasourceId?: string
}>()

const emit = defineEmits<{
  built: [draft: WorkflowDefinitionDTO]
  cancel: []
}>()

const description = ref('')
const workflowName = ref('')
const loading = ref(false)
const errorMessage = ref('')

async function build() {
  if (!description.value.trim() || loading.value) return
  if (!props.datasourceId) {
    errorMessage.value = '请先在节点中配置数据源'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const draft = await buildWorkflowDraft({
      datasourceId: props.datasourceId,
      description: description.value.trim(),
      workflowName: workflowName.value.trim() || undefined,
    })
    emit('built', draft)
  } catch (err) {
    errorMessage.value = (err as Error).message || 'AI 构建失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="ai-workflow-dialog" role="dialog" aria-modal="true">
    <div class="ai-workflow-dialog__backdrop" @click="$emit('cancel')" />
    <div class="ai-workflow-dialog__panel">
      <header class="ai-workflow-dialog__header">
        <span class="ai-workflow-dialog__icon">✦</span>
        <span>AI 构建工作流</span>
        <button class="ai-workflow-dialog__close" @click="$emit('cancel')" aria-label="关闭">✕</button>
      </header>
      <div class="ai-workflow-dialog__body">
        <p class="ai-workflow-dialog__hint">
          描述你想分析的业务需求，AI 将自动生成包含数据查询和可视化节点的工作流草稿。
        </p>
        <label class="ai-workflow-dialog__label">工作流名称（可选）</label>
        <input v-model="workflowName"
               class="ai-workflow-dialog__input"
               placeholder="AI 会根据需求自动命名"
               :disabled="loading" />
        <label class="ai-workflow-dialog__label">需求描述 <span class="ai-workflow-dialog__required">*</span></label>
        <textarea v-model="description"
                  class="ai-workflow-dialog__textarea"
                  rows="4"
                  placeholder="例如：分析过去 7 天各渠道的 DAU 趋势，用折线图展示"
                  :disabled="loading"
                  @keydown.ctrl.enter="build" />
        <div v-if="errorMessage" class="ai-workflow-dialog__error">{{ errorMessage }}</div>
      </div>
      <footer class="ai-workflow-dialog__footer">
        <button class="ai-workflow-dialog__btn ai-workflow-dialog__btn--primary"
                @click="build"
                :disabled="!description.trim() || loading">
          {{ loading ? 'AI 构建中...' : '开始构建' }}
        </button>
        <button class="ai-workflow-dialog__btn" @click="$emit('cancel')">取消</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.ai-workflow-dialog {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ai-workflow-dialog__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
}
.ai-workflow-dialog__panel {
  position: relative;
  width: min(560px, 94vw);
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 16px;
  display: grid;
  overflow: hidden;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.5);
}
.ai-workflow-dialog__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  border-bottom: 1px solid #1e293b;
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}
.ai-workflow-dialog__icon { color: #818cf8; font-size: 16px; }
.ai-workflow-dialog__close {
  margin-left: auto;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 6px;
  border-radius: 6px;
}
.ai-workflow-dialog__close:hover { color: #e2e8f0; }
.ai-workflow-dialog__body {
  padding: 16px 18px;
  display: grid;
  gap: 10px;
}
.ai-workflow-dialog__hint {
  font-size: 13px;
  color: #64748b;
  margin: 0;
  line-height: 1.5;
}
.ai-workflow-dialog__label {
  font-size: 12px;
  color: #94a3b8;
}
.ai-workflow-dialog__required { color: #f87171; }
.ai-workflow-dialog__input,
.ai-workflow-dialog__textarea {
  width: 100%;
  background: #020617;
  border: 1px solid #334155;
  border-radius: 10px;
  color: #e2e8f0;
  padding: 10px 12px;
  font-size: 13px;
  font-family: inherit;
}
.ai-workflow-dialog__textarea { resize: vertical; line-height: 1.5; }
.ai-workflow-dialog__input:focus,
.ai-workflow-dialog__textarea:focus { outline: none; border-color: #818cf8; }
.ai-workflow-dialog__error {
  color: #f87171;
  font-size: 12px;
  padding: 8px 12px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 8px;
}
.ai-workflow-dialog__footer {
  display: flex;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid #1e293b;
}
.ai-workflow-dialog__btn {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid #334155;
  background: rgba(51, 65, 85, 0.3);
  color: #cbd5e1;
}
.ai-workflow-dialog__btn:hover:not(:disabled) { background: rgba(51, 65, 85, 0.6); }
.ai-workflow-dialog__btn:disabled { opacity: 0.45; cursor: not-allowed; }
.ai-workflow-dialog__btn--primary {
  background: rgba(99, 102, 241, 0.25);
  border-color: rgba(99, 102, 241, 0.5);
  color: #a5b4fc;
}
.ai-workflow-dialog__btn--primary:hover:not(:disabled) { background: rgba(99, 102, 241, 0.4); }
</style>
