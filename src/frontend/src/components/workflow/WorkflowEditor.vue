<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import type { NodeMetaDTO, WorkflowDefinitionDTO } from '@/types/contract'
import type { Component } from 'vue'
import NodePalette from './NodePalette.vue'
import VersionHistoryPanel from './VersionHistoryPanel.vue'
import TriggerPanel from './TriggerPanel.vue'
import WorkflowNodeRenderer from './WorkflowNodeRenderer.vue'
import WorkflowNodePanelRenderer from './WorkflowNodePanelRenderer.vue'
import AiWorkflowDialog from '@/components/ai/AiWorkflowDialog.vue'
import { useWorkflowStore } from '@/stores/workflow'
import { storeToRefs } from 'pinia'
import { getBusinessNodeType, WORKFLOW_RENDERER_NODE_TYPE } from '@/adapters/workflow-graph'

const workflow = useWorkflowStore()
const { nodes, edges, selectedNode, workflowName, workflowId, saving, workflowList, loading, viewport } = storeToRefs(workflow)

type RightPanel = 'config' | 'versions' | 'triggers'
const rightPanel = ref<RightPanel>('config')
const rightPanelVisible = ref(true)
const showAiDialog = ref(false)
const canvasPatternColor = ref('rgba(133, 133, 173, 0.12)')

function updateWorkflowThemeVars() {
  const styles = getComputedStyle(document.documentElement)
  const edgeColor = styles.getPropertyValue('--iap-workflow-link-active').trim() || '#5289ff'
  const dotColor = styles.getPropertyValue('--iap-canvas-dot-color').trim() || 'rgba(133, 133, 173, 0.12)'
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
  const sqlNode = nodes.value.find(n => getBusinessNodeType(n) === 'sql_query' && n.data.config?.datasourceId)
  const id = sqlNode?.data.config?.datasourceId
  return typeof id === 'string' && id ? id : undefined
})

const nodeTypes = computed<Record<string, Component>>(() => ({
  [WORKFLOW_RENDERER_NODE_TYPE]: WorkflowNodeRenderer,
}))

const defaultEdgeOptions = ref({
  type: 'smoothstep',
  animated: true,
  style: { stroke: '#5289ff', strokeWidth: 2 },
})

let themeObserver: MutationObserver | undefined

onMounted(() => {
  workflow.loadList().catch(() => undefined)
  updateWorkflowThemeVars()
  themeObserver = new MutationObserver(() => updateWorkflowThemeVars())
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme'],
  })
})

onUnmounted(() => {
  themeObserver?.disconnect()
})

function handleAddNode(meta: NodeMetaDTO) {
  workflow.addNode(meta, { x: 160 + nodes.value.length * 40, y: 120 + nodes.value.length * 24 })
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
      <button class="workflow-editor__button workflow-editor__button--ai" @click="showAiDialog = true">✦ AI 创建</button>
      <select class="workflow-editor__select" :disabled="loading" :value="workflowId ?? ''" @change="handleSelectWorkflow">
        <option value="">选择已保存工作流</option>
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
        {{ rightPanelVisible ? '▶' : '◀' }}
      </button>
    </header>
    <AiWorkflowDialog
      v-if="showAiDialog"
      :datasource-id="aiDatasourceId"
      @built="handleAiDraftBuilt"
      @cancel="showAiDialog = false"
    />
    <NodePalette @add="handleAddNode" />
    <div class="workflow-editor__canvas">
      <VueFlow
        :nodes="nodes"
        :edges="edges"
        :node-types="nodeTypes"
        :default-edge-options="defaultEdgeOptions"
        :viewport="viewport"
        @nodes-change="workflow.onNodesChange"
        @connect="workflow.onConnect"
        @node-click="workflow.onNodeClick"
        @viewport-change-end="handleViewportChange"
      >
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
  border-radius: 10px;
  background: var(--iap-input-bg);
  color: var(--iap-text-primary);
  padding: 10px 12px;
  outline: none;
}
.workflow-editor__name-input:hover,
.workflow-editor__select:hover {
  background: var(--iap-input-bg-hover);
}
.workflow-editor__name-input:focus,
.workflow-editor__select:focus {
  border-color: var(--iap-input-border-focus);
  background: var(--iap-input-bg-focus);
  box-shadow: 0 0 0 3px var(--iap-accent-ring);
}
.workflow-editor__name-input {
  min-width: 220px;
}
.workflow-editor__select {
  min-width: 220px;
}
.workflow-editor__button {
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
  padding: 10px 10px;
  min-width: 36px;
  font-size: 11px;
  margin-left: auto;
}
.workflow-editor__button--ai {
  border-color: var(--iap-ai-btn-border);
  background: var(--iap-ai-btn-bg);
  color: var(--iap-ai-btn-text);
}
.workflow-editor__button--ai:hover:not(:disabled) {
  background: var(--iap-ai-btn-hover);
}
.workflow-editor__button:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.workflow-editor__canvas {
  position: relative;
  background: var(--iap-canvas-overlay), var(--iap-canvas-bg);
}
:deep(.vue-flow__pane) {
  background: transparent;
}
:deep(.vue-flow__edge-path) {
  stroke: var(--iap-workflow-link-active);
}
:deep(.vue-flow__controls) {
  border: 1px solid var(--iap-divider);
  border-radius: var(--iap-radius-lg);
  background: var(--iap-panel-bg);
  box-shadow: var(--iap-shadow-panel);
}
:deep(.vue-flow__controls-button) {
  border-bottom: 1px solid var(--iap-divider);
  background: var(--iap-panel-bg);
  color: var(--iap-text-secondary);
}
:deep(.vue-flow__controls-button:hover) {
  background: var(--iap-surface-hover);
  color: var(--iap-text-primary);
}
</style>
