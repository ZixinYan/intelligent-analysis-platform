<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import AppIcon from '@/components/icons/AppIcon.vue'
import { buildWorkflow, buildWorkflowDraft } from '@/api/ai'
import { cancelAgentTask, getAgentTaskStatus } from '@/api/task'
import type {
  AiClarificationAnswerDTO,
  AiClarificationQuestionDTO,
  AiWorkflowBuildMode,
  AiWorkflowBuildRequestDTO,
  AsyncTaskStatusDTO,
  WorkflowDefinitionDTO,
} from '@/types/contract'

const props = defineProps<{
  datasourceId?: string
}>()

const emit = defineEmits<{
  built: [draft: WorkflowDefinitionDTO]
  cancel: []
}>()

const description = ref('')
const workflowName = ref('')
const loadingMode = ref<AiWorkflowBuildMode | ''>('')
const errorMessage = ref('')
const conversationId = ref<string>()
const agentTaskId = ref<string>()
const clarifications = ref<AiClarificationQuestionDTO[]>([])
const clarificationValues = ref<Record<string, string>>({})
const taskStatus = ref<AsyncTaskStatusDTO>()
const taskPolling = ref(false)
const taskPollTimer = ref<number>()

const hasClarification = computed(() => clarifications.value.length > 0)
const taskStatusText = computed(() => {
  switch (taskStatus.value?.status) {
    case 'PENDING':
      return '任务已创建，等待调度'
    case 'QUEUED':
      return '任务排队中'
    case 'RUNNING':
      return '任务执行中'
    case 'SUCCEEDED':
      return '任务已完成'
    case 'FAILED':
      return '任务执行失败'
    case 'CANCELLED':
      return '任务已取消'
    default:
      return ''
  }
})
const isBusy = computed(() => Boolean(loadingMode.value) || taskPolling.value)
const clarificationAnswerCount = computed(() => Object.values(clarificationValues.value).filter(value => value.trim()).length)

function clearTaskPolling() {
  if (taskPollTimer.value) {
    window.clearTimeout(taskPollTimer.value)
    taskPollTimer.value = undefined
  }
}

function resetClarifications(nextQuestions: AiClarificationQuestionDTO[]) {
  clarifications.value = nextQuestions
  clarificationValues.value = Object.fromEntries(
    nextQuestions.map(question => [question.key, '']),
  )
}

function buildRequest(mode: AiWorkflowBuildMode): AiWorkflowBuildRequestDTO {
  return {
    datasourceId: props.datasourceId!,
    description: description.value.trim(),
    workflowName: workflowName.value.trim() || undefined,
    buildMode: mode,
    runAndSave: mode === 'RUN_AND_SAVE',
    conversationId: conversationId.value,
    agentTaskId: agentTaskId.value,
    clarificationAnswers: hasClarification.value
      ? clarifications.value
          .map<AiClarificationAnswerDTO>((question) => ({
            key: question.key,
            value: clarificationValues.value[question.key]?.trim() ?? '',
          }))
          .filter(answer => answer.value)
      : undefined,
  }
}

function applyDraft(draft: WorkflowDefinitionDTO) {
  clearTaskPolling()
  taskPolling.value = false
  taskStatus.value = undefined
  clarifications.value = []
  clarificationValues.value = {}
  conversationId.value = undefined
  agentTaskId.value = undefined
  emit('built', draft)
}

function applyClarifications(nextQuestions: AiClarificationQuestionDTO[], nextConversationId?: string) {
  clearTaskPolling()
  taskPolling.value = false
  taskStatus.value = undefined
  conversationId.value = nextConversationId ?? conversationId.value
  resetClarifications(nextQuestions)
}

async function handleTaskState(nextTask: AsyncTaskStatusDTO) {
  taskStatus.value = nextTask
  if (nextTask.agentTaskId) {
    agentTaskId.value = nextTask.agentTaskId
  }
  if (nextTask.status === 'SUCCEEDED') {
    taskPolling.value = false
    clearTaskPolling()
    const draft = nextTask.result?.variables?.draft as WorkflowDefinitionDTO | undefined
    if (draft) {
      applyDraft(draft)
      return
    }
    errorMessage.value = '任务已完成，但暂未返回工作流草稿'
    return
  }
  if (nextTask.clarification) {
    applyClarifications([nextTask.clarification])
    return
  }
  if (nextTask.status === 'FAILED') {
    taskPolling.value = false
    clearTaskPolling()
    errorMessage.value = nextTask.error?.message || 'AI 任务执行失败，请重试'
    return
  }
  if (nextTask.status === 'CANCELLED') {
    taskPolling.value = false
    clearTaskPolling()
    errorMessage.value = 'AI 任务已取消'
  }
}

async function pollAcceptedTask(taskId: string) {
  taskPolling.value = true
  clearTaskPolling()
  const poll = async () => {
    try {
      const nextTask = await getAgentTaskStatus(taskId)
      await handleTaskState(nextTask)
      if (!taskPolling.value) {
        return
      }
      taskPollTimer.value = window.setTimeout(poll, 2000)
    }
    catch (err) {
      taskPolling.value = false
      clearTaskPolling()
      errorMessage.value = (err as Error).message || 'AI 任务状态查询失败'
    }
  }
  await poll()
}

async function build(mode: AiWorkflowBuildMode = 'DRAFT_ONLY') {
  if (!description.value.trim() || loadingMode.value || taskPolling.value) return
  if (!props.datasourceId) {
    errorMessage.value = '请先在节点中配置数据源'
    return
  }
  loadingMode.value = mode
  errorMessage.value = ''
  try {
    if (mode === 'DRAFT_ONLY' && !hasClarification.value && !agentTaskId.value) {
      const draft = await buildWorkflowDraft({
        datasourceId: props.datasourceId,
        description: description.value.trim(),
        workflowName: workflowName.value.trim() || undefined,
        responseMode: 'LEGACY_DRAFT',
        buildMode: 'DRAFT_ONLY',
      })
      applyDraft(draft)
      return
    }
    const result = await buildWorkflow(buildRequest(mode))
    if (result.agentTaskId) {
      agentTaskId.value = result.agentTaskId
    }
    if (result.responseType === 'DRAFT' && result.draft) {
      applyDraft(result.draft)
      return
    }
    if (result.responseType === 'CLARIFICATION') {
      applyClarifications(result.clarifications ?? [], typeof result.metadata?.conversationId === 'string' ? result.metadata.conversationId : undefined)
      return
    }
    if (result.responseType === 'TASK_ACCEPTED') {
      taskStatus.value = {
        taskId: result.agentTaskId ?? agentTaskId.value ?? '',
        agentTaskId: result.agentTaskId ?? agentTaskId.value,
        status: 'QUEUED',
      }
      const taskId = result.agentTaskId ?? agentTaskId.value
      if (!taskId) {
        throw new Error('AI 任务已受理，但缺少任务标识')
      }
      await pollAcceptedTask(taskId)
      return
    }
    throw new Error('AI 构建返回了不支持的结果类型')
  }
  catch (err) {
    errorMessage.value = (err as Error).message || 'AI 构建失败，请重试'
  }
  finally {
    loadingMode.value = ''
  }
}

async function cancelTaskPolling() {
  const taskId = agentTaskId.value ?? taskStatus.value?.taskId
  if (!taskId) {
    return
  }
  try {
    await cancelAgentTask(taskId)
  }
  catch (err) {
    errorMessage.value = (err as Error).message || '取消任务失败'
  }
  finally {
    taskPolling.value = false
    clearTaskPolling()
  }
}

function handleClose() {
  taskPolling.value = false
  clearTaskPolling()
  emit('cancel')
}

onBeforeUnmount(() => {
  taskPolling.value = false
  clearTaskPolling()
})

function updateClarificationValue(key: string, event: Event) {
  clarificationValues.value = {
    ...clarificationValues.value,
    [key]: (event.target as HTMLInputElement | HTMLTextAreaElement).value,
  }
}
</script>

<template>
  <div class="ai-workflow-dialog" role="dialog" aria-modal="true">
    <div class="ai-workflow-dialog__backdrop" @click="handleClose" />
    <div class="ai-workflow-dialog__panel">
      <header class="ai-workflow-dialog__header">
        <span class="ai-workflow-dialog__icon"><AppIcon name="ai" :size="16" /></span>
        <span>AI 构建工作流</span>
        <button class="ai-workflow-dialog__close" @click="handleClose" aria-label="关闭"><AppIcon name="close" :size="14" /></button>
      </header>
      <div class="ai-workflow-dialog__body">
        <p class="ai-workflow-dialog__hint">
          描述你想分析的业务需求，AI 将自动生成包含数据查询和可视化节点的工作流草稿。
        </p>
        <label class="ai-workflow-dialog__label">工作流名称（可选）</label>
        <input v-model="workflowName"
               class="ai-workflow-dialog__input"
               placeholder="AI 会根据需求自动命名"
               :disabled="isBusy" />
        <label class="ai-workflow-dialog__label">需求描述 <span class="ai-workflow-dialog__required">*</span></label>
        <textarea v-model="description"
                  class="ai-workflow-dialog__textarea"
                  rows="4"
                  placeholder="例如：分析过去 7 天各渠道的 DAU 趋势，用折线图展示"
                  :disabled="isBusy"
                  @keydown.ctrl.enter="build('DRAFT_ONLY')" />
        <div v-if="hasClarification" class="ai-workflow-dialog__clarification">
          <div class="ai-workflow-dialog__clarification-title">
            需要补充 {{ clarifications.length }} 个信息项
            <span class="ai-workflow-dialog__clarification-count">已填写 {{ clarificationAnswerCount }}</span>
          </div>
          <div v-for="question in clarifications" :key="question.key" class="ai-workflow-dialog__clarification-item">
            <label class="ai-workflow-dialog__label">
              {{ question.label }}
              <span v-if="question.required" class="ai-workflow-dialog__required">*</span>
            </label>
            <textarea
              v-if="question.inputType === 'TEXTAREA'"
              class="ai-workflow-dialog__textarea ai-workflow-dialog__textarea--clarification"
              rows="2"
              :placeholder="question.hint || '请输入补充信息'"
              :value="clarificationValues[question.key] ?? ''"
              :disabled="isBusy"
              @input="updateClarificationValue(question.key, $event)"
            />
            <input
              v-else
              class="ai-workflow-dialog__input"
              :placeholder="question.hint || '请输入补充信息'"
              :value="clarificationValues[question.key] ?? ''"
              :disabled="isBusy"
              @input="updateClarificationValue(question.key, $event)"
            />
            <div v-if="question.hint" class="ai-workflow-dialog__clarification-hint">{{ question.hint }}</div>
          </div>
        </div>
        <div v-if="taskStatusText" class="ai-workflow-dialog__task-card">
          <div class="ai-workflow-dialog__task-title">任务状态</div>
          <div class="ai-workflow-dialog__task-status">{{ taskStatusText }}</div>
          <div v-if="taskStatus?.agentTaskId || taskStatus?.taskId" class="ai-workflow-dialog__task-meta">
            任务 ID：{{ taskStatus?.agentTaskId || taskStatus?.taskId }}
          </div>
          <div v-if="typeof taskStatus?.progress === 'number'" class="ai-workflow-dialog__task-meta">
            进度：{{ taskStatus?.progress }}%
          </div>
        </div>
        <div v-if="errorMessage" class="ai-workflow-dialog__error">{{ errorMessage }}</div>
      </div>
      <footer class="ai-workflow-dialog__footer">
        <button class="ai-workflow-dialog__btn ai-workflow-dialog__btn--primary"
                @click="build('DRAFT_ONLY')"
                :disabled="!description.trim() || isBusy">
          {{ loadingMode === 'DRAFT_ONLY' ? 'AI 构建中...' : hasClarification ? '提交补充并生成' : '生成草稿' }}
        </button>
        <button class="ai-workflow-dialog__btn ai-workflow-dialog__btn--secondary"
                @click="build('RUN_AND_SAVE')"
                :disabled="!description.trim() || isBusy">
          {{ loadingMode === 'RUN_AND_SAVE' ? '保存中...' : hasClarification ? '提交补充并保存' : '保存并返回草稿' }}
        </button>
        <button v-if="taskPolling"
                class="ai-workflow-dialog__btn ai-workflow-dialog__btn--danger"
                @click="cancelTaskPolling">
          取消任务
        </button>
        <button class="ai-workflow-dialog__btn" @click="handleClose" :disabled="Boolean(loadingMode)">取消</button>
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
.ai-workflow-dialog__icon { color: #818cf8; display: grid; place-items: center; }
.ai-workflow-dialog__close {
  margin-left: auto;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  display: grid;
  place-items: center;
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
.ai-workflow-dialog__textarea--clarification { min-height: 88px; }
.ai-workflow-dialog__input:focus,
.ai-workflow-dialog__textarea:focus { outline: none; border-color: #818cf8; }
.ai-workflow-dialog__clarification {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #334155;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.6);
}
.ai-workflow-dialog__clarification-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #cbd5e1;
}
.ai-workflow-dialog__clarification-count {
  color: #64748b;
}
.ai-workflow-dialog__clarification-item {
  display: grid;
  gap: 6px;
}
.ai-workflow-dialog__clarification-hint {
  font-size: 11px;
  color: #64748b;
}
.ai-workflow-dialog__task-card {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.12);
  border: 1px solid rgba(99, 102, 241, 0.35);
}
.ai-workflow-dialog__task-title {
  font-size: 12px;
  color: #a5b4fc;
}
.ai-workflow-dialog__task-status {
  font-size: 13px;
  color: #e2e8f0;
}
.ai-workflow-dialog__task-meta {
  font-size: 11px;
  color: #94a3b8;
}
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
.ai-workflow-dialog__btn--secondary {
  background: rgba(45, 212, 191, 0.2);
  border-color: rgba(45, 212, 191, 0.45);
  color: #99f6e4;
}
.ai-workflow-dialog__btn--secondary:hover:not(:disabled) { background: rgba(45, 212, 191, 0.32); }
.ai-workflow-dialog__btn--danger {
  background: rgba(239, 68, 68, 0.18);
  border-color: rgba(239, 68, 68, 0.4);
  color: #fca5a5;
}
.ai-workflow-dialog__btn--danger:hover:not(:disabled) { background: rgba(239, 68, 68, 0.3); }
</style>
