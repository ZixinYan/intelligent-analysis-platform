import { ref } from 'vue'
import { inferNodeSchema } from '@/api/node-definition'
import { inferQuerySchema, validateQuery } from '@/api/query'
import type { QueryRequestDTO, SchemaInferResultDTO, ValidateResultDTO } from '@/types/contract'
import type { WorkflowNode } from '@/types/workflow'
import { getBusinessNodeType } from '@/adapters/workflow-graph'

export function buildQueryRequest(node: WorkflowNode): QueryRequestDTO {
  const nodeData = node.data
  const datasourceId = nodeData.config.datasourceId
  const sqlTemplate = nodeData.config.sqlTemplate

  // Validate required fields
  if (!datasourceId || typeof datasourceId !== 'string' || datasourceId.trim() === '') {
    throw new Error('数据源ID不能为空')
  }

  if (!sqlTemplate || typeof sqlTemplate !== 'string' || sqlTemplate.trim() === '') {
    throw new Error('SQL语句不能为空')
  }

  return {
    datasourceId: String(datasourceId).trim(),
    sql: String(sqlTemplate).trim(),
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
  const schema = ref<SchemaInferResultDTO>()
  const error = ref<string>()

  async function runValidate(node: WorkflowNode) {
    loading.value = true
    error.value = undefined
    try {
      const request = buildQueryRequest(node)
      validation.value = await validateQuery(request)
      return validation.value
    }
    catch (err) {
      const message = err instanceof Error ? err.message : '校验失败'
      error.value = message
      console.error('SQL validation failed:', err)
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
      schema.value = getBusinessNodeType(node) === 'sql_query'
        ? await inferQuerySchema(buildQueryRequest(node))
        : await inferNodeSchema(getBusinessNodeType(node))
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
    schema,
    error,
    runValidate,
    runSchemaInfer,
  }
}
