import { ref } from 'vue'
import { listNodeDefinitions } from '@/api/node-definition'
import type { NodeMetaDTO } from '@/types/contract'

export function useNodeDefinitions() {
  const loading = ref(false)
  const error = ref<string>()
  const definitions = ref<NodeMetaDTO[]>([])

  async function load() {
    loading.value = true
    error.value = undefined
    try {
      definitions.value = await listNodeDefinitions()
      return definitions.value
    }
    catch (err) {
      error.value = err instanceof Error ? err.message : '加载失败'
      throw err
    }
    finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    definitions,
    load,
  }
}
