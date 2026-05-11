<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import type { NodeMetaDTO } from '@/types/contract'
import type { Component } from 'vue'
import AnalysisNodeShell from './AnalysisNodeShell.vue'
import NodePalette from './NodePalette.vue'
import NodeConfigPanel from './NodeConfigPanel.vue'
import { useWorkflowStore } from '@/stores/workflow'
import { storeToRefs } from 'pinia'

const workflow = useWorkflowStore()
const { nodes, edges, selectedNode, workflowName, workflowId, saving, workflowList, loading } = storeToRefs(workflow)

const nodeTypes = computed<Record<string, Component>>(() => ({
  'analysis-node': AnalysisNodeShell,
}))

const defaultEdgeOptions = {
  type: 'smoothstep',
  animated: true,
  style: { stroke: '#3b82f6', strokeWidth: 2 },
}

onMounted(() => {
  workflow.loadList().catch(() => undefined)
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
</script>

<template>
  <div class="workflow-editor">
    <header class="workflow-editor__toolbar">
      <input v-model="workflowName" class="workflow-editor__name-input" placeholder="工作流名称" />
      <button class="workflow-editor__button workflow-editor__button--primary" :disabled="saving" @click="workflow.save()">
        {{ saving ? '保存中...' : '保存工作流' }}
      </button>
      <button class="workflow-editor__button" @click="handleReset">新建画布</button>
      <select class="workflow-editor__select" :disabled="loading" :value="workflowId ?? ''" @change="handleSelectWorkflow">
        <option value="">选择已保存工作流</option>
        <option v-for="item in workflowList" :key="item.workflowId" :value="item.workflowId">
          {{ item.workflowName }}
        </option>
      </select>
    </header>
    <NodePalette @add="handleAddNode" />
    <div class="workflow-editor__canvas">
      <VueFlow
        :nodes="nodes"
        :edges="edges"
        :node-types="nodeTypes"
        :default-edge-options="defaultEdgeOptions"
        fit-view-on-init
        @nodes-change="workflow.onNodesChange"
        @connect="workflow.onConnect"
        @node-click="workflow.onNodeClick"
      >
        <Background pattern-color="#1e293b" />
        <Controls />
      </VueFlow>
    </div>
    <NodeConfigPanel :node="selectedNode" />
  </div>
</template>

<style scoped>
.workflow-editor {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 280px 1fr 360px;
  grid-template-rows: auto 1fr;
}
.workflow-editor__toolbar {
  grid-column: 1 / -1;
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #1e293b;
  background: rgba(2, 6, 23, 0.92);
}
.workflow-editor__name-input,
.workflow-editor__select {
  border: 1px solid #334155;
  border-radius: 10px;
  background: #020617;
  color: #cbd5e1;
  padding: 10px 12px;
}
.workflow-editor__name-input {
  min-width: 220px;
}
.workflow-editor__select {
  min-width: 220px;
}
.workflow-editor__button {
  border: 1px solid #334155;
  border-radius: 10px;
  background: transparent;
  color: #cbd5e1;
  padding: 10px 14px;
  cursor: pointer;
}
.workflow-editor__button--primary {
  border-color: transparent;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
}
.workflow-editor__canvas {
  position: relative;
  background: radial-gradient(circle at top, rgba(30, 41, 59, 0.45), #020617 60%);
}
</style>
