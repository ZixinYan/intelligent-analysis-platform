import { computed, ref } from 'vue'
import { runQueryAsync } from '@/api/query'
import { cancelTask, getTaskStatus } from '@/api/task'
import type { AsyncTaskStatusDTO, DatasetDTO, ErrorInfoDTO, ExecutionStatus, QueryRequestDTO } from '@/types/contract'

const TERMINAL_STATUS: ExecutionStatus[] = ['SUCCESS', 'FAILED', 'CANCELLED']

export function useAsyncTask(pollInterval = 1500) {
  const taskId = ref<string>()
  const status = ref<ExecutionStatus>()
  const progress = ref(0)
  const dataset = ref<DatasetDTO>()
  const error = ref<ErrorInfoDTO>()
  const loading = ref(false)
  const polling = ref(false)
  const task = ref<AsyncTaskStatusDTO>()

  let timer: ReturnType<typeof setTimeout> | undefined
  let currentRequestId = 0

  const isRunning = computed(() => status.value === 'PENDING' || status.value === 'RUNNING')

  function clearTimer() {
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }

  function applyTask(nextTask: AsyncTaskStatusDTO) {
    task.value = nextTask
    taskId.value = nextTask.taskId
    status.value = nextTask.status
    progress.value = Number(nextTask.progress ?? 0)
    dataset.value = nextTask.dataset
    error.value = nextTask.error
  }

  function stopPolling() {
    clearTimer()
    polling.value = false
    loading.value = false
  }

  async function poll(requestId = currentRequestId): Promise<AsyncTaskStatusDTO | undefined> {
    if (!taskId.value) {
      return undefined
    }
    const nextTask = await getTaskStatus(taskId.value)
    if (requestId !== currentRequestId) {
      return nextTask
    }
    applyTask(nextTask)
    if (TERMINAL_STATUS.includes(nextTask.status)) {
      stopPolling()
      return nextTask
    }
    polling.value = true
    timer = setTimeout(() => {
      poll(requestId).catch((err) => {
        if (requestId !== currentRequestId) {
          return
        }
        error.value = {
          message: err instanceof Error ? err.message : '轮询任务状态失败',
        }
        status.value = 'FAILED'
        stopPolling()
      })
    }, pollInterval)
    return nextTask
  }

  async function submit(payload: QueryRequestDTO) {
    reset()
    loading.value = true
    const requestId = ++currentRequestId
    const response = await runQueryAsync(payload)
    if (requestId !== currentRequestId) {
      return
    }
    taskId.value = response.taskId
    status.value = response.status
    progress.value = 0
    polling.value = true
    await poll(requestId)
  }

  async function cancel() {
    if (!taskId.value) {
      return
    }
    const currentTaskId = taskId.value
    clearTimer()
    polling.value = false
    await cancelTask(currentTaskId)
    status.value = 'CANCELLED'
    loading.value = false
  }

  function reset() {
    clearTimer()
    currentRequestId += 1
    taskId.value = undefined
    status.value = undefined
    progress.value = 0
    dataset.value = undefined
    error.value = undefined
    loading.value = false
    polling.value = false
    task.value = undefined
  }

  return {
    taskId,
    status,
    progress,
    dataset,
    error,
    loading,
    polling,
    task,
    isRunning,
    submit,
    poll,
    cancel,
    reset,
  }
}
