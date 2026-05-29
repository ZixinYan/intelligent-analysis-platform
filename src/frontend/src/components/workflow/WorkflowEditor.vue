<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { VueFlow, SelectionMode, ConnectionMode } from '@vue-flow/core'
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
import WorkflowRunPanel from './WorkflowRunPanel.vue'
import WorkflowNodeRenderer from './WorkflowNodeRenderer.vue'
import WorkflowNodePanelRenderer from './WorkflowNodePanelRenderer.vue'
import WorkflowInsertEdge from './WorkflowInsertEdge.vue'
import AiWorkflowDialog from '@/components/ai/AiWorkflowDialog.vue'
import { storeToRefs } from 'pinia'
import { useWorkflowStore, useWorkflowGraphStore, useWorkflowDebugStore, useWorkflowDefinitionStore } from '@/stores/workflow'
import type { WorkflowNode } from '@/types/workflow'
import { getBusinessNodeType } from '@/adapters/workflow-graph'

const workflow = useWorkflowStore()
const defStore = useWorkflowDefinitionStore()
const graphStore = useWorkflowGraphStore()
const debugStore = useWorkflowDebugStore()
const { isStreaming } = storeToRefs(debugStore)
const nodes = computed(() => graphStore.nodes)
const edges = computed(() => graphStore.edges)
const selectedNode = computed(() => graphStore.selectedNode)
const workflowName = computed({
  get: () => defStore.workflowName,
  set: value => {
    defStore.workflowName = value
  },
})
const workflowId = computed(() => defStore.workflowId)
const saving = computed(() => defStore.saving)
const workflowList = computed(() => defStore.workflowList)
const loading = computed(() => defStore.loading)
const viewport = computed(() => graphStore.viewport)

const LEFT_PANEL_WIDTH = 280
const RIGHT_PANEL_DEFAULT_WIDTH = 360
const RIGHT_PANEL_MIN_WIDTH = 280
const RIGHT_PANEL_HANDLE_WIDTH = 10
const CANVAS_MIN_WIDTH = 520

type RightPanel = 'config' | 'versions' | 'triggers' | 'run'
const rightPanel = ref<RightPanel>('config')
const rightPanelVisible = ref(true)
const rightPanelWidth = ref(RIGHT_PANEL_DEFAULT_WIDTH)
const isResizingPanel = ref(false)
const editorRef = ref<HTMLElement | null>(null)
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

const panelLayoutStyle = computed(() => ({
  '--workflow-right-panel-width': rightPanelVisible.value ? `${rightPanelWidth.value}px` : '0px',
  '--workflow-right-panel-handle-width': rightPanelVisible.value ? `${RIGHT_PANEL_HANDLE_WIDTH}px` : '0px',
}))

let resizeStartX = 0
let resizeStartWidth = RIGHT_PANEL_DEFAULT_WIDTH

function getAvailableRightPanelWidth() {
  const editorWidth = editorRef.value?.clientWidth ?? 0
  const availableWidth = editorWidth - LEFT_PANEL_WIDTH - RIGHT_PANEL_HANDLE_WIDTH - CANVAS_MIN_WIDTH
  return Math.max(0, availableWidth)
}

function getMinRightPanelWidth() {
  const availableWidth = getAvailableRightPanelWidth()
  if (availableWidth >= RIGHT_PANEL_MIN_WIDTH) {
    return RIGHT_PANEL_MIN_WIDTH
  }
  return Math.max(0, Math.min(RIGHT_PANEL_DEFAULT_WIDTH, availableWidth))
}

function getMaxRightPanelWidth() {
  const availableWidth = getAvailableRightPanelWidth()
  return Math.max(getMinRightPanelWidth(), availableWidth)
}

function clampRightPanelWidth(nextWidth: number) {
  const minWidth = getMinRightPanelWidth()
  const maxWidth = getMaxRightPanelWidth()
  return Math.min(Math.max(nextWidth, minWidth), maxWidth)
}

function stopPanelResize() {
  isResizingPanel.value = false
  window.removeEventListener('pointermove', handlePanelResize)
  window.removeEventListener('pointerup', stopPanelResize)
  window.removeEventListener('pointercancel', stopPanelResize)
}

function handlePanelResize(event: PointerEvent) {
  if (!isResizingPanel.value) {
    return
  }
  const delta = event.clientX - resizeStartX
  rightPanelWidth.value = clampRightPanelWidth(resizeStartWidth - delta)
}

function startPanelResize(event: PointerEvent) {
  if (!rightPanelVisible.value) {
    return
  }
  event.preventDefault()
  event.stopPropagation()
  resizeStartX = event.clientX
  resizeStartWidth = rightPanelWidth.value
  isResizingPanel.value = true
  window.addEventListener('pointermove', handlePanelResize)
  window.addEventListener('pointerup', stopPanelResize)
  window.addEventListener('pointercancel', stopPanelResize)
}

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
  // Check if there's actually something selected to delete
  const hasSelection = graphStore.selectedNodeIds.length > 0 || graphStore.selectedEdgeIds.length > 0
  if (!hasSelection) {
    return
  }
  event.preventDefault()
  event.stopPropagation()
  workflow.deleteSelection()
}

let themeObserver: MutationObserver | undefined

watch(editorRef, async (element) => {
  if (!element) {
    return
  }
  await nextTick()
  rightPanelWidth.value = clampRightPanelWidth(rightPanelWidth.value)
}, { flush: 'post' })

onMounted(() => {
  workflow.loadList().catch(() => undefined)
  updateWorkflowThemeVars()
  rightPanelWidth.value = clampRightPanelWidth(RIGHT_PANEL_DEFAULT_WIDTH)
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
  stopPanelResize()
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
  <div
    ref="editorRef"
    class="workflow-editor"
    :class="{
      'workflow-editor--panel-hidden': !rightPanelVisible,
      'workflow-editor--resizing': isResizingPanel,
    }"
    :style="panelLayoutStyle"
  >
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
        class="workflow-editor__button"
        :class="{ 'workflow-editor__button--run': rightPanel === 'run' || isStreaming, 'workflow-editor__button--active': rightPanel === 'run' }"
        :disabled="!workflowId"
        @click="togglePanel('run')"
      >
        <span v-if="isStreaming" class="workflow-editor__run-spinner" />
        <span v-else>▷</span>
        运行
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
        :connection-mode="ConnectionMode.Strict"
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
    <div
      v-if="rightPanelVisible"
      class="workflow-editor__resizer"
      role="separator"
      aria-label="调整右侧面板宽度"
      aria-orientation="vertical"
      @pointerdown="startPanelResize"
    >
      <span class="workflow-editor__resizer-line" />
    </div>
    <aside v-if="rightPanelVisible" class="workflow-editor__side-panel">
      <WorkflowNodePanelRenderer v-if="rightPanel === 'config'" :node="selectedNode" />
      <WorkflowRunPanel
        v-else-if="rightPanel === 'run'"
      />
      <VersionHistoryPanel
        v-else-if="rightPanel === 'versions' && workflowId"
        :workflow-id="workflowId"
        @rollback="workflow.load(workflowId!)"
      />
      <TriggerPanel
        v-else-if="rightPanel === 'triggers' && workflowId"
        :workflow-id="workflowId"
      />
    </aside>
  </div>
</template>

<style scoped>
.workflow-editor {
  --workflow-right-panel-width: 360px;
  --workflow-right-panel-handle-width: 10px;
  min-height: 100vh;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) var(--workflow-right-panel-handle-width) var(--workflow-right-panel-width);
  grid-template-rows: auto 1fr;
}
.workflow-editor--panel-hidden {
  grid-template-columns: 280px minmax(0, 1fr) 0px 0px;
}
.workflow-editor--resizing {
  user-select: none;
  cursor: col-resize;
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
  min-width: 0;
  background: var(--iap-canvas-bg);
  background-image: var(--iap-canvas-overlay);
}
.workflow-editor__resizer {
  position: relative;
  display: flex;
  align-items: stretch;
  justify-content: center;
  cursor: col-resize;
  touch-action: none;
  background: transparent;
}
.workflow-editor__resizer-line {
  width: 1px;
  height: 100%;
  background: var(--iap-divider);
  transition: background 0.15s ease;
}
.workflow-editor__resizer:hover .workflow-editor__resizer-line,
.workflow-editor--resizing .workflow-editor__resizer-line {
  background: var(--iap-input-border-focus);
}
.workflow-editor__button--run {
  border-color: var(--iap-btn-primary-bg, #296dff);
  color: var(--iap-btn-primary-bg, #296dff);
}
.workflow-editor__run-spinner {
  display: inline-block;
  width: 11px;
  height: 11px;
  border: 2px solid rgba(41, 109, 255, 0.25);
  border-top-color: currentColor;
  border-radius: 50%;
  animation: wf-spin 0.7s linear infinite;
}
@keyframes wf-spin { to { transform: rotate(360deg); } }
.workflow-editor__side-panel {
  min-width: 0;
  height: 100%;
  overflow: hidden;
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
