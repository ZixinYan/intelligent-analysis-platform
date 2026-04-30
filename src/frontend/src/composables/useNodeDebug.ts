import { ref } from 'vue'
import { inferNodeSchema } from '@/api/node-definition'
import { inferQuerySchema, previewQuery, validateQuery } from '@/api/query'
import type { QueryRequestDTO, QueryResultDTO, SchemaInferResultDTO, ValidateResultDTO } from '@/types/contract'
import type { WorkflowNode } from '@/types/workflow'

export function buildQueryRequest(node: WorkflowNode): QueryRequestDTO {
  const nodeData = node.data
  return {
    datasourceId: String(nodeData.config.datasourceId ?? ''),
    sql: String(nodeData.config.sqlTemplate ?? ''),
    option: {
      timeoutMs: Number(nodeData.config.timeoutMs ?? 10000),
      limit: Number(nodeData.config.limit ?? 500),
      useCache: Boolean(nodeData.config.enableCache ?? true),
    },
  }
}

export function useNodeDebug() {
  const loading = ref(false)
  const validation = ref<ValidateResultDTO>()
  const preview = ref<QueryResultDTO>()
  const schema = ref<SchemaInferResultDTO>()
  const error = ref<string>()

  async function runValidate(node: WorkflowNode) {
    loading.value = true
    error.value = undefined
    try {
      validation.value = await validateQuery(buildQueryRequest(node))
      return validation.value
    }
    catch (err) {
      error.value = err instanceof Error ? err.message : '校验失败'
      throw err
    }
    finally {
      loading.value = false
    }
  }

  async function runPreview(node: WorkflowNode) {
    loading.value = true
    error.value = undefined
    try {
      preview.value = await previewQuery(buildQueryRequest(node))
      return preview.value
    }
    catch (err) {
      error.value = err instanceof Error ? err.message : '预览失败'
      throw err
    }
    finally {
      loading.value = false
    }
  }

  async function runSchemaInfer(node: WorkflowNode) {
    loading.value = true
    error.value = undefined
    try {
      schema.value = node.data.nodeType === 'sql_query'
        ? await inferQuerySchema(buildQueryRequest(node))
        : await inferNodeSchema(node.data.nodeType)
      return schema.value
    }
    catch (err) {
      error.value = err instanceof Error ? err.message : '推断失败'
      throw err
    }
    finally {
      loading.value = false
    }
  }

  return {
    loading,
    validation,
    preview,
    schema,
    error,
    runValidate,
    runPreview,
    runSchemaInfer,
  }
}
