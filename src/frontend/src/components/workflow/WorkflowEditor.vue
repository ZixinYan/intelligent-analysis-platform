<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { VueFlow, SelectionMode } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import AppIcon from '@/components/icons/AppIcon.vue'
import type { NodeMetaDTO, WorkflowDefinitionDTO } from '@/types/contract'
import type { WorkflowInsertTrigger } from './insert-types'
import NodePalette from './NodePalette.vue'
import NodeInsertPicker from './NodeInsertPicker.vue'
import VersionHistoryPanel from './VersionHistoryPanel.vue'
import TriggerPanel from './TriggerPanel.vue'
import WorkflowNodeRenderer from './WorkflowNodeRenderer.vue'
import WorkflowNodePanelRenderer from './WorkflowNodePanelRenderer.vue'
import WorkflowInsertEdge from './WorkflowInsertEdge.vue'
import AiWorkflowDialog from '@/components/ai/AiWorkflowDialog.vue'
import { useWorkflowStore } from '@/stores/workflow'
import type { WorkflowNode } from '@/types/workflow'
import { getBusinessNodeType } from '@/adapters/workflow-graph'

const workflow = useWorkflowStore()
const nodes = computed(() => workflow.nodes)
const edges = computed(() => workflow.edges)
const selectedNode = computed(() => workflow.selectedNode)
const workflowName = computed({
  get: () => workflow.workflowName,
  set: value => {
    workflow.workflowName = value
  },
})
const workflowId = computed(() => workflow.workflowId)
const saving = computed(() => workflow.saving)
const workflowList = computed(() => workflow.workflowList)
const loading = computed(() => workflow.loading)
const viewport = computed(() => workflow.viewport)

type RightPanel = 'config' | 'versions' | 'triggers'
const rightPanel = ref<RightPanel>('config')
const rightPanelVisible = ref(true)
const showAiDialog = ref(false)
const insertTrigger = ref<WorkflowInsertTrigger | null>(null)
const insertPickerVisible = ref(false)
const canvasPatternColor = ref('rgba(103, 111, 131, 0.18)')

function updateWorkflowThemeVars() {
  const styles = getComputedStyle(document.documentElement)
  const edgeColor = styles.getPropertyValue('--iap-workflow-link-active').trim() || '#296dff'
  const dotColor = styles.getPropertyValue('--iap-canvas-dot-color').trim() || 'rgba(103, 111, 131, 0.18)'
  defaultEdgeOptions.value = {
    type: 'smoothstep',
    animated: true,
    style: { stroke: edgeColor, strokeWidth: 2 },
  }
  canvasPatternColor.value = dotColor
}

function togglePanel(panel: Exclude<RightPanel, 'config'>) {
  rightPanel.value = rightPanel.value === panel ? 'config' : panel
}

const aiDatasourceId = computed(() => {
  const sqlNode = nodes.value.find((n: WorkflowNode) => getBusinessNodeType(n) === 'sql_query' && n.data.config?.datasourceId)
  const id = sqlNode?.data.config?.datasourceId
  return typeof id === 'string' && id ? id : undefined
})

const defaultEdgeOptions = ref({
  type: 'smoothstep',
  animated: true,
  style: { stroke: '#296dff', strokeWidth: 2 },
})

function shouldIgnoreDeleteShortcut(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  const tagName = target.tagName.toLowerCase()
  return tagName === 'input'
    || tagName === 'textarea'
    || tagName === 'select'
    || target.isContentEditable
    || target.getAttribute('role') === 'textbox'
    || Boolean(target.closest('[contenteditable="true"]'))
    || Boolean(target.closest('[role="textbox"]'))
}

function handleWindowKeydown(event: KeyboardEvent) {
  if (event.key !== 'Delete' && event.key !== 'Backspace') {
    return
  }
  if (shouldIgnoreDeleteShortcut(event.target)) {
    return
  }
  event.preventDefault()
  workflow.deleteSelection()
}

let themeObserver: MutationObserver | undefined

onMounted(() => {
  workflow.loadList().catch(() => undefined)
  updateWorkflowThemeVars()
  themeObserver = new MutationObserver(() => updateWorkflowThemeVars())
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme'],
  })
  window.addEventListener('keydown', handleWindowKeydown)
})

onUnmounted(() => {
  themeObserver?.disconnect()
  window.removeEventListener('keydown', handleWindowKeydown)
})

function handleAddNode(meta: NodeMetaDTO) {
  workflow.addNode(meta, { x: 160 + nodes.value.length * 40, y: 120 + nodes.value.length * 24 })
}

function openInsertPicker(trigger: WorkflowInsertTrigger) {
  insertTrigger.value = trigger
  insertPickerVisible.value = true
}

function closeInsertPicker() {
  insertPickerVisible.value = false
  insertTrigger.value = null
}

function handleInsertNodeSelect(meta: NodeMetaDTO) {
  const trigger = insertTrigger.value
  if (!trigger) {
    return
  }
  if (trigger.kind === 'node-output') {
    workflow.insertNodeAfter({ sourceNodeId: trigger.nodeId, sourceHandle: trigger.sourceHandle, meta })
  }
  if (trigger.kind === 'node-input') {
    workflow.insertNodeBefore({ targetNodeId: trigger.nodeId, targetHandle: trigger.targetHandle, meta })
  }
  if (trigger.kind === 'edge') {
    workflow.insertNodeOnEdge({ edgeId: trigger.edgeId, meta })
  }
  closeInsertPicker()
}

function handleReset() {
  workflow.reset()
}

function handleSelectWorkflow(event: Event) {
  const workflowId = (event.target as HTMLSelectElement).value
  if (!workflowId) {
    return
  }
  workflow.load(workflowId).catch(() => undefined)
}

function handleAiDraftBuilt(draft: WorkflowDefinitionDTO) {
  workflow.hydrate({ ...draft, workflowId: '' })
  showAiDialog.value = false
}

function handleViewportChange(payload: { x: number; y: number; zoom: number }) {
  workflow.setViewport(payload)
}
</script>

<template>
  <div class="workflow-editor" :class="{ 'workflow-editor--panel-hidden': !rightPanelVisible }">
    <header class="workflow-editor__toolbar">
      <input v-model="workflowName" class="workflow-editor__name-input" placeholder="工作流名称" />
      <button class="workflow-editor__button workflow-editor__button--primary" :disabled="saving" @click="workflow.save()">
        {{ saving ? '保存中...' : '保存工作流' }}
      </button>
      <button class="workflow-editor__button" @click="handleReset">新建画布</button>
      <button class="workflow-editor__button workflow-editor__button--ai" @click="showAiDialog = true">
        <AppIcon name="ai" :size="14" />
        <span>AI 创建</span>
      </button>
      <select class="workflow-editor__select" :class="{ 'workflow-editor__select--placeholder': !workflowId }" :disabled="loading" :value="workflowId ?? ''" @change="handleSelectWorkflow">
        <option value="" disabled hidden>选择已保存工作流</option>
        <option v-for="item in workflowList" :key="item.workflowId" :value="item.workflowId">
          {{ item.workflowName }}
        </option>
      </select>
      <button
        class="workflow-editor__button"
        :class="{ 'workflow-editor__button--active': rightPanel === 'versions' }"
        :disabled="!workflowId"
        @click="togglePanel('versions')"
      >
        版本历史
      </button>
      <button
        class="workflow-editor__button"
        :class="{ 'workflow-editor__button--active': rightPanel === 'triggers' }"
        :disabled="!workflowId"
        @click="togglePanel('triggers')"
      >
        触发器
      </button>
      <button
        class="workflow-editor__button workflow-editor__button--icon"
        :title="rightPanelVisible ? '隐藏右侧面板' : '显示右侧面板'"
        @click="rightPanelVisible = !rightPanelVisible"
      >
        <AppIcon :name="rightPanelVisible ? 'chevron-right' : 'chevron-left'" :size="14" />
      </button>
    </header>
    <AiWorkflowDialog
      v-if="showAiDialog"
      :datasource-id="aiDatasourceId"
      @built="handleAiDraftBuilt"
      @cancel="showAiDialog = false"
    />
    <NodePalette @add="handleAddNode" />
    <NodeInsertPicker
      :visible="insertPickerVisible"
      :anchor="insertTrigger?.anchor ?? { x: 24, y: 24 }"
      :trigger="insertTrigger"
      @close="closeInsertPicker"
      @select="handleInsertNodeSelect"
    />
    <div class="workflow-editor__canvas">
      <VueFlow
        :nodes="nodes"
        :edges="edges"
        :default-edge-options="defaultEdgeOptions"
        :viewport="viewport"
        :elements-selectable="true"
        :nodes-focusable="true"
        :edges-focusable="true"
        :selection-on-drag="true"
        :selection-mode="SelectionMode.Partial as any"
        :multi-selection-key-code="['Meta', 'Control']"
        :delete-key-code="null"
        @nodes-change="workflow.onNodesChange"
        @edges-change="workflow.onEdgesChange"
        @connect="workflow.onConnect"
        @node-click="workflow.onNodeClick"
        @pane-click="workflow.onPaneClick"
        @viewport-change-end="handleViewportChange"
      >
        <template #node-workflow-node="nodeProps">
          <WorkflowNodeRenderer v-bind="nodeProps" @open-insert-picker="openInsertPicker" />
        </template>
        <template #edge-workflow-insert-edge="edgeProps">
          <WorkflowInsertEdge v-bind="edgeProps" @insert="openInsertPicker({ kind: 'edge', edgeId: $event.edgeId, anchor: $event.anchor })" />
        </template>
        <Background :pattern-color="canvasPatternColor" />
        <Controls />
      </VueFlow>
    </div>
    <WorkflowNodePanelRenderer v-if="rightPanelVisible && rightPanel === 'config'" :node="selectedNode" />
    <VersionHistoryPanel
      v-else-if="rightPanelVisible && rightPanel === 'versions' && workflowId"
      :workflow-id="workflowId"
      @rollback="workflow.load(workflowId!)"
    />
    <TriggerPanel
      v-else-if="rightPanelVisible && rightPanel === 'triggers' && workflowId"
      :workflow-id="workflowId"
    />
  </div>
</template>

<style scoped>
.workflow-editor {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 280px 1fr 360px;
  grid-template-rows: auto 1fr;
}
.workflow-editor--panel-hidden {
  grid-template-columns: 280px 1fr 0px;
}
.workflow-editor__toolbar {
  grid-column: 1 / -1;
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid var(--iap-divider);
  background: var(--iap-toolbar-bg);
  backdrop-filter: blur(18px);
}
.workflow-editor__name-input,
.workflow-editor__select {
  border: 1px solid var(--iap-input-border);
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-input-bg-focus) 100%);
  color: var(--iap-text-primary);
  padding: 10px 14px;
  outline: none;
  box-shadow: var(--iap-select-shadow);
}
.workflow-editor__name-input:hover,
.workflow-editor__select:hover {
  background: linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-hover) 100%);
  border-color: var(--iap-divider-strong);
}
.workflow-editor__name-input:focus,
.workflow-editor__select:focus {
  border-color: var(--iap-input-border-focus);
  background: linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-focus) 100%);
  box-shadow: var(--iap-select-shadow), var(--iap-select-shadow-focus);
}
.workflow-editor__name-input {
  min-width: 220px;
}
.workflow-editor__select {
  min-width: 220px;
  padding-right: 44px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-input-bg-focus) 100%);
  background-repeat: no-repeat, no-repeat;
  background-position: right 14px center, center;
  background-size: 14px 14px, auto;
}
.workflow-editor__select:hover {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-hover) 100%);
}
.workflow-editor__select:focus {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%23155aef' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-focus) 100%);
}
.workflow-editor__select--placeholder {
  color: var(--iap-text-placeholder);
}
.workflow-editor__button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--iap-btn-secondary-border);
  border-radius: 10px;
  background: var(--iap-btn-secondary-bg);
  color: var(--iap-btn-secondary-text);
  padding: 10px 14px;
  cursor: pointer;
}
.workflow-editor__button:hover:not(:disabled) {
  background: var(--iap-btn-secondary-hover);
  color: var(--iap-btn-secondary-text-strong);
}
.workflow-editor__button--primary {
  border-color: transparent;
  background: var(--iap-btn-primary-bg);
  color: var(--iap-btn-primary-text);
}
.workflow-editor__button--primary:hover:not(:disabled) {
  background: var(--iap-btn-primary-hover);
}
.workflow-editor__button--active {
  border-color: var(--iap-input-border-focus);
  color: var(--iap-text-accent);
}
.workflow-editor__button--icon {
  margin-left: auto;
  justify-content: center;
  min-width: 40px;
}
.workflow-editor__button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.workflow-editor__button--ai {
  border-color: var(--iap-ai-btn-border);
  background: var(--iap-ai-btn-bg);
  color: var(--iap-ai-btn-text);
}
.workflow-editor__button--ai:hover:not(:disabled) {
  background: var(--iap-ai-btn-hover);
}
.workflow-editor__canvas {
  position: relative;
  background: var(--iap-canvas-bg);
  background-image: var(--iap-canvas-overlay);
}

:deep(.vue-flow__pane) {
  cursor: default;
}

:deep(.vue-flow__background) {
  background-color: transparent;
}

:deep(.vue-flow__controls) {
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--iap-shadow-panel);
}

:deep(.vue-flow__controls-button) {
  width: 34px;
  height: 34px;
  border: 1px solid var(--iap-divider) !important;
  background: rgba(255, 255, 255, 0.92) !important;
  color: var(--iap-text-secondary) !important;
}

:deep(.vue-flow__controls-button:hover) {
  background: #ffffff !important;
  color: var(--iap-text-primary) !important;
}
</style>
