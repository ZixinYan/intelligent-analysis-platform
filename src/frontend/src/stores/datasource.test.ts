import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDatasourceStore } from '@/stores/datasource'

const api = vi.hoisted(() => ({
  listDatasources: vi.fn(),
  createDatasource: vi.fn(),
  updateDatasource: vi.fn(),
  removeDatasource: vi.fn(),
  testDatasourceConnection: vi.fn(),
}))

vi.mock('@/api/datasource', () => api)

describe('useDatasourceStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads datasource list and maps options', async () => {
    api.listDatasources.mockResolvedValue({
      records: [
        { id: 'ds-1', name: '订单库', type: 'MYSQL', host: '127.0.0.1', port: 3306, database: 'orders', username: 'reader' },
      ],
      total: 1,
      page: 1,
      pageSize: 100,
    })

    const store = useDatasourceStore()
    await store.load()

    expect(store.datasources).toHaveLength(1)
    expect(store.options).toEqual([{ label: '订单库', value: 'ds-1' }])
  })

  it('creates and removes datasource', async () => {
    const created = { id: 'ds-2', name: '分析库', type: 'CLICKHOUSE' as const, host: 'localhost', port: 8123, database: 'analysis', username: 'analyst' }
    api.createDatasource.mockResolvedValue(created)
    api.removeDatasource.mockResolvedValue(undefined)

    const store = useDatasourceStore()
    await store.create({ ...created, password: 'secret' })
    await store.remove('ds-2')

    expect(api.createDatasource).toHaveBeenCalled()
    expect(api.removeDatasource).toHaveBeenCalledWith('ds-2')
    expect(store.datasources).toEqual([])
  })
})
