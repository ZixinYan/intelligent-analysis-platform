import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import NodeInsertPicker from './NodeInsertPicker.vue'
import { useNodeRegistryStore } from '@/stores/node-registry'

const anchor = { x: 120, y: 80 }

describe('NodeInsertPicker', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const registry = useNodeRegistryStore()
    registry.nodes = [
      { protocolVersion: '1', metadataVersion: '1', nodeType: 'sql_query', displayName: 'SQL 查询', description: '执行 SQL', category: 'QUERY', nodeVersion: '1', inputPorts: [], outputPorts: [], tags: [] },
      { protocolVersion: '1', metadataVersion: '1', nodeType: 'aggregate', displayName: '聚合', description: '聚合统计', category: 'COMPUTE', nodeVersion: '1', inputPorts: [], outputPorts: [], tags: [] },
    ]
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders groups and emits select', async () => {
    const wrapper = mount(NodeInsertPicker, {
      props: {
        visible: true,
        anchor,
        trigger: { kind: 'node-output', nodeId: 'node-1', sourceHandle: 'output', anchor },
      },
      attachTo: document.body,
    })

    expect(document.body.textContent).toContain('SQL 查询')
    const firstItem = document.body.querySelector('.picker__item') as HTMLButtonElement | null
    expect(firstItem).toBeTruthy()
    firstItem?.click()
    expect(wrapper.emitted('select')?.[0]?.[0]).toMatchObject({ nodeType: 'sql_query' })
  })

  it('emits close on overlay click', async () => {
    const wrapper = mount(NodeInsertPicker, {
      props: {
        visible: true,
        anchor,
      },
      attachTo: document.body,
    })

    const overlay = document.body.querySelector('.picker-overlay') as HTMLDivElement | null
    expect(overlay).toBeTruthy()
    overlay?.click()
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
