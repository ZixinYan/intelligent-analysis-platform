import { computed, ref, toValue, watch } from 'vue'
import client from '@/api/client'
import type { OptionDTO, PanelFieldDTO, SchemaInferResultDTO } from '@/types/contract'

export type OptionsLoadError = { message: string } | null

function resolveStaticOptions(field: PanelFieldDTO) {
  return field.options ?? []
}

function mapRemoteOptions(source: unknown, field: PanelFieldDTO) {
  const records = Array.isArray(source)
    ? source
    : Array.isArray((source as { records?: unknown[] } | undefined)?.records)
      ? (source as { records: unknown[] }).records
      : []
  return records.map((item) => {
    // Handle plain string/number arrays (e.g. table names from /datasources/{id}/tables)
    if (typeof item === 'string' || typeof item === 'number') {
      return { label: String(item), value: String(item) }
    }
    const record = item as Record<string, unknown>
    return {
      label: String(record[field.optionsSource?.labelField ?? 'label'] ?? record.label ?? record.name ?? record.id ?? ''),
      value: String(record[field.optionsSource?.valueField ?? 'value'] ?? record.value ?? record.id ?? ''),
    }
  })
}

function resolveSchemaFieldOptions(field: PanelFieldDTO, schema?: SchemaInferResultDTO) {
  const acceptedCapabilities = field.optionsSource?.acceptedCapabilities ?? []
  return (schema?.fields ?? [])
    .filter((item) => {
      if (!acceptedCapabilities.length) {
        return true
      }
      return acceptedCapabilities.every(capability => item.capabilities?.includes(capability))
    })
    .map<OptionDTO>(item => ({
      label: item.displayName || item.name,
      value: item.name,
    }))
}

function resolveUri(uri: string, model?: Record<string, unknown>): string {
  if (!model) return uri
  return uri.replace(/\{(\w+)\}/g, (_, key) => {
    const val = model[key]
    return val != null ? String(val) : ''
  })
}

export function useOptionsLoader(
  field: () => PanelFieldDTO,
  schema?: () => SchemaInferResultDTO | undefined,
  model?: () => Record<string, unknown> | undefined,
) {
  const options = ref<OptionDTO[]>([])
  const loading = ref(false)
  const error = ref<string>()
  let requestId = 0

  async function loadRemoteOptions(currentField: PanelFieldDTO) {
    if (!currentField.optionsSource?.uri) {
      options.value = []
      return
    }
    const resolvedUri = resolveUri(currentField.optionsSource.uri, model?.())
    if (resolvedUri.includes('{')) {
      // 模板变量未填充完整，跳过
      options.value = []
      return
    }
    const currentRequestId = ++requestId
    loading.value = true
    error.value = undefined
    try {
      const { data } = await client.get(resolvedUri)
      if (currentRequestId !== requestId) {
        return
      }
      options.value = mapRemoteOptions(data.data, currentField)
    }
    catch (err) {
      if (currentRequestId === requestId) {
        error.value = err instanceof Error ? err.message : '加载选项失败'
        options.value = []
      }
    }
    finally {
      if (currentRequestId === requestId) {
        loading.value = false
      }
    }
  }

  watch([() => field(), () => schema?.(), () => model?.()], async ([currentField, currentSchema]) => {
    const sourceType = currentField.optionsSource?.type
    if (!sourceType || sourceType === 'static') {
      options.value = resolveStaticOptions(currentField)
      loading.value = false
      error.value = undefined
      return
    }
    if (sourceType === 'schema-fields') {
      options.value = resolveSchemaFieldOptions(currentField, currentSchema)
      loading.value = false
      error.value = undefined
      return
    }
    await loadRemoteOptions(currentField)
  }, { immediate: true, deep: true })

  return {
    options: computed(() => options.value),
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    reload: async () => {
      const currentField = toValue(field)
      if (currentField.optionsSource?.type === 'remote') {
        await loadRemoteOptions(currentField)
        return
      }
      if (currentField.optionsSource?.type === 'schema-fields') {
        options.value = resolveSchemaFieldOptions(currentField, toValue(schema))
        return
      }
      options.value = resolveStaticOptions(currentField)
    },
  }
}
