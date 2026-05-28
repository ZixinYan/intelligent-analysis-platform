import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, nextTick } from 'vue'

const workflowStoreMock = {
  nodes: [],
  edges: [],
  selectedNode: undefined,
  workflowName: '测试工作流',
  workflowId: 'wf-1',
  saving: false,
  workflowList: [{ workflowId: 'wf-1', workflowName: '测试工作流' }],
  loading: false,
  viewport: { x: 0, y: 0, zoom: 1 },
  selectedNodeIds: [],
  selectedEdgeIds: [],
  loadList: vi.fn().mockResolvedValue(undefined),
  deleteSelection: vi.fn(),
  addNode: vi.fn(),
  insertNodeAfter: vi.fn(),
  insertNodeBefore: vi.fn(),
  insertNodeOnEdge: vi.fn(),
  reset: vi.fn(),
  load: vi.fn().mockResolvedValue(undefined),
  hydrate: vi.fn(),
  setViewport: vi.fn(),
  save: vi.fn(),
  onNodesChange: vi.fn(),
  onEdgesChange: vi.fn(),
  onConnect: vi.fn(),
  onNodeClick: vi.fn(),
  onPaneClick: vi.fn(),
}

vi.mock('@/stores/workflow', () => ({
  useWorkflowStore: () => workflowStoreMock,
}))

vi.mock('@/adapters/workflow-graph', () => ({
  getBusinessNodeType: () => 'sql_query',
}))

vi.mock('@vue-flow/background', () => ({
  Background: defineComponent({ name: 'BackgroundStub', template: '<div class="background-stub" />' }),
}))

vi.mock('@vue-flow/controls', () => ({
  Controls: defineComponent({ name: 'ControlsStub', template: '<div class="controls-stub" />' }),
}))

vi.mock('@vue-flow/core', () => ({
  VueFlow: defineComponent({ name: 'VueFlowStub', template: '<div class="vue-flow-stub"><slot /></div>' }),
  SelectionMode: { Partial: 'partial' },
}))

import WorkflowEditor from './WorkflowEditor.vue'

function mountEditor() {
  return mount(WorkflowEditor, {
    attachTo: document.body,
    global: {
      stubs: {
        AppIcon: { template: '<span class="app-icon" />' },
        AiWorkflowDialog: { template: '<div class="ai-dialog-stub" />' },
        NodePalette: { template: '<aside class="node-palette-stub" />' },
        NodeInsertPicker: { template: '<div class="insert-picker-stub" />' },
        WorkflowNodeRenderer: { template: '<div class="node-renderer-stub" />' },
        WorkflowInsertEdge: { template: '<div class="insert-edge-stub" />' },
        WorkflowNodePanelRenderer: { template: '<div class="panel-renderer-stub" />' },
        VersionHistoryPanel: { template: '<div class="version-panel-stub" />' },
        TriggerPanel: { template: '<div class="trigger-panel-stub" />' },
      },
    },
  })
}

async function mountEditorWithWidth(width: number) {
  const wrapper = mountEditor()
  const editor = wrapper.find('.workflow-editor')
  defineEditorWidth(editor.element, width)
  workflowStoreMock.loadList.mockClear()
  await wrapper.unmount()

  const mounted = mountEditor()
  const mountedEditor = mounted.find('.workflow-editor')
  defineEditorWidth(mountedEditor.element, width)
  await nextTick()
  return { wrapper: mounted, editor: mountedEditor }
}

function defineEditorWidth(element: Element, width: number) {
  Object.defineProperty(element, 'clientWidth', { value: width, configurable: true })
}

function dispatchPointer(name: string, clientX: number) {
  const event = new Event(name)
  Object.defineProperty(event, 'clientX', { value: clientX })
  window.dispatchEvent(event)
}

describe('WorkflowEditor', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.assign(workflowStoreMock, {
      nodes: [],
      edges: [],
      selectedNode: undefined,
      workflowName: '测试工作流',
      workflowId: 'wf-1',
      saving: false,
      workflowList: [{ workflowId: 'wf-1', workflowName: '测试工作流' }],
      loading: false,
      viewport: { x: 0, y: 0, zoom: 1 },
      selectedNodeIds: [],
      selectedEdgeIds: [],
    })
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders responsive initial width and restores it after hide/show', async () => {
    const { wrapper, editor } = await mountEditorWithWidth(1680)

    expect((editor.element as HTMLElement).style.getPropertyValue('--workflow-right-panel-width')).toBe('360px')
    expect(wrapper.find('.workflow-editor__resizer').exists()).toBe(true)

    const toggleButton = wrapper.find('[title="隐藏右侧面板"]')
    await toggleButton.trigger('click')
    expect(wrapper.find('.workflow-editor__resizer').exists()).toBe(false)
    expect((editor.element as HTMLElement).style.getPropertyValue('--workflow-right-panel-width')).toBe('0px')

    const showButton = wrapper.find('[title="显示右侧面板"]')
    await showButton.trigger('click')
    expect((editor.element as HTMLElement).style.getPropertyValue('--workflow-right-panel-width')).toBe('360px')
  })

  it('shrinks initial width on narrow layouts', async () => {
    const { editor } = await mountEditorWithWidth(1000)

    expect((editor.element as HTMLElement).style.getPropertyValue('--workflow-right-panel-width')).toBe('190px')
  })

  it('resizes panel and clamps width', async () => {
    const { wrapper, editor } = await mountEditorWithWidth(1680)

    await wrapper.find('.workflow-editor__resizer').trigger('pointerdown', {
      clientX: 1000,
      button: 0,
    })

    dispatchPointer('pointermove', 900)
    await nextTick()
    expect((editor.element as HTMLElement).style.getPropertyValue('--workflow-right-panel-width')).toBe('460px')

    dispatchPointer('pointermove', 1500)
    await nextTick()
    expect((editor.element as HTMLElement).style.getPropertyValue('--workflow-right-panel-width')).toBe('280px')

    dispatchPointer('pointermove', 100)
    await nextTick()
    expect((editor.element as HTMLElement).style.getPropertyValue('--workflow-right-panel-width')).toBe('590px')

    dispatchPointer('pointerup', 100)
    await nextTick()
    expect(wrapper.find('.workflow-editor').classes()).not.toContain('workflow-editor--resizing')
  })
})
