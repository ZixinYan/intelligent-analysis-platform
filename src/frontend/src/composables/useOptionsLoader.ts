import { computed, ref, toValue, watch } from 'vue'
import client from '@/api/client'
import type { OptionDTO, PanelFieldDTO, SchemaInferResultDTO } from '@/types/contract'

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
    try {
      const { data } = await client.get(resolvedUri)
      if (currentRequestId !== requestId) {
        return
      }
      options.value = mapRemoteOptions(data.data, currentField)
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
      return
    }
    if (sourceType === 'schema-fields') {
      options.value = resolveSchemaFieldOptions(currentField, currentSchema)
      loading.value = false
      return
    }
    await loadRemoteOptions(currentField)
  }, { immediate: true, deep: true })

  return {
    options: computed(() => options.value),
    loading: computed(() => loading.value),
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
