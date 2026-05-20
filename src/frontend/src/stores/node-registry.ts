import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { listNodeDefinitions } from '@/api/node-definition'
import { normalizeNodeType, toRawNodeType } from '@/constants/analysis-nodes'
import type { NodeMetaDTO } from '@/types/contract'

export const useNodeRegistryStore = defineStore('node-registry', () => {
  const nodes = ref<NodeMetaDTO[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string>()

  const sortedNodes = computed(() => [...nodes.value].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)))

  async function load() {
    if (loading.value) {
      return
    }
    loading.value = true
    error.value = undefined
    try {
      nodes.value = await listNodeDefinitions()
      loaded.value = true
    }
    catch (err) {
      error.value = err instanceof Error ? err.message : '加载节点定义失败'
      throw err
    }
    finally {
      loading.value = false
    }
  }

  function findNodeMeta(nodeType: string) {
    const rawType = toRawNodeType(nodeType)
    return nodes.value.find(item => item.nodeType === rawType)
      ?? nodes.value.find(item => normalizeNodeType(item.nodeType) === normalizeNodeType(nodeType))
  }

  return {
    nodes,
    loading,
    loaded,
    error,
    sortedNodes,
    load,
    findNodeMeta,
  }
})
