import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { useAsyncTask } from '@/composables/useAsyncTask'

const queryApi = vi.hoisted(() => ({
  runQueryAsync: vi.fn(),
}))

const taskApi = vi.hoisted(() => ({
  getTaskStatus: vi.fn(),
  cancelTask: vi.fn(),
}))

vi.mock('@/api/query', () => queryApi)
vi.mock('@/api/task', () => taskApi)

describe('useAsyncTask', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('polls until success', async () => {
    queryApi.runQueryAsync.mockResolvedValue({ taskId: 'task-1', status: 'PENDING' })
    taskApi.getTaskStatus
      .mockResolvedValueOnce({ taskId: 'task-1', taskType: 'QUERY', status: 'RUNNING', progress: 40 })
      .mockResolvedValueOnce({ taskId: 'task-1', taskType: 'QUERY', status: 'SUCCESS', progress: 100, dataset: { rows: [{ id: 1 }] } })

    const task = useAsyncTask(10)
    const promise = task.submit({ datasourceId: 'ds-1', sql: 'select 1' })
    await Promise.resolve()
    await vi.advanceTimersByTimeAsync(10)
    await promise

    expect(task.status.value).toBe('SUCCESS')
    expect(task.dataset.value?.rows).toEqual([{ id: 1 }])
  })

  it('cancels running task', async () => {
    queryApi.runQueryAsync.mockResolvedValue({ taskId: 'task-2', status: 'RUNNING' })
    taskApi.getTaskStatus.mockResolvedValue({ taskId: 'task-2', taskType: 'QUERY', status: 'RUNNING', progress: 10 })
    taskApi.cancelTask.mockResolvedValue(undefined)

    const task = useAsyncTask(10)
    task.submit({ datasourceId: 'ds-1', sql: 'select 1' })
    await Promise.resolve()
    await task.cancel()

    expect(taskApi.cancelTask).toHaveBeenCalledWith('task-2')
    expect(task.status.value).toBe('CANCELLED')
  })
})
