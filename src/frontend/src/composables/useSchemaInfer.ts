import { ref } from 'vue'
import { inferNodeSchema } from '@/api/node-definition'
import type { SchemaInferResultDTO } from '@/types/contract'

export function useSchemaInfer() {
  const loading = ref(false)
  const schema = ref<SchemaInferResultDTO>()

  async function load(nodeType: string) {
    loading.value = true
    try {
      schema.value = await inferNodeSchema(nodeType)
      return schema.value
    }
    finally {
      loading.value = false
    }
  }

  return {
    loading,
    schema,
    load,
  }
}
