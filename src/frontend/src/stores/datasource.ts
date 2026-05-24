import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  createDatasource,
  listDatasources,
  removeDatasource,
  testDatasourceConnection,
  updateDatasource,
} from '@/api/datasource'
import type {
  DatasourceCreateRequestDTO,
  DatasourceDTO,
  DatasourceTestConnectionResultDTO,
  DatasourceUpdateRequestDTO,
  OptionDTO,
} from '@/types/contract'

export const useDatasourceStore = defineStore('datasource', () => {
  const datasources = ref<DatasourceDTO[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string>()

  const options = computed<OptionDTO[]>(() => datasources.value.map(item => ({
    label: item.database ?? item.datasourceName,
    value: item.id ?? item.datasourceId,
  })))

  async function load(force = false) {
    if (loading.value) {
      return
    }
    if (loaded.value && !force) {
      return
    }
    loading.value = true
    error.value = undefined
    try {
      const page = await listDatasources({ page: 1, pageSize: 100 })
      datasources.value = page.items
      loaded.value = true
    }
    catch (err) {
      error.value = err instanceof Error ? err.message : '加载数据源失败'
      throw err
    }
    finally {
      loading.value = false
    }
  }

  async function create(payload: DatasourceCreateRequestDTO) {
    const datasource = await createDatasource(payload)
    datasources.value = [datasource, ...datasources.value.filter(item => item.id !== datasource.id)]
    loaded.value = true
    return datasource
  }

  async function update(id: string, payload: DatasourceUpdateRequestDTO) {
    const datasource = await updateDatasource(id, payload)
    datasources.value = datasources.value.map(item => item.id === id ? datasource : item)
    return datasource
  }

  async function remove(id: string) {
    await removeDatasource(id)
    datasources.value = datasources.value.filter(item => item.id !== id)
  }

  async function test(id: string): Promise<DatasourceTestConnectionResultDTO> {
    return testDatasourceConnection(id)
  }

  function findById(id?: string) {
    return datasources.value.find(item => item.id === id)
  }

  return {
    datasources,
    loading,
    loaded,
    error,
    options,
    load,
    create,
    update,
    remove,
    test,
    findById,
  }
})
