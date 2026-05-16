import client, { requestContextHeaders, unwrapResponse } from './client'
import type {
  AiChartRecommendRequestDTO,
  AiSqlRequestDTO,
  AiWorkflowBuildRequestDTO,
  ChartRecommendationDTO,
  WorkflowDefinitionDTO,
} from '@/types/contract'

/**
 * AI SQL 生成（SSE 流式）。
 * 返回 AsyncIterable<string>（每次 yield 一个文字 token）。
 * 调用方负责 for-await-of 消费，收到 error 事件时 throw。
 */
export async function* generateSqlStream(
  request: AiSqlRequestDTO,
  signal?: AbortSignal,
): AsyncIterable<string> {
  const response = await fetch('/api/v1/ai/sql/generate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...requestContextHeaders,
    },
    body: JSON.stringify(request),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`AI SQL generate request failed: ${response.status} ${response.statusText}`)
  }

  const decoder = new TextDecoder()
  const reader = response.body.getReader()
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() ?? ''
      for (const block of blocks) {
        const lines = block.split('\n')
        const eventLine = lines.find(l => l.startsWith('event:'))
        const dataLine = lines.find(l => l.startsWith('data:'))
        const eventName = eventLine ? eventLine.slice('event:'.length).trim() : 'token'
        const data = dataLine ? dataLine.slice('data:'.length).trim() : ''
        if (eventName === 'error') throw new Error(data || 'AI generation error')
        if (eventName === 'done') return
        if (eventName === 'token' && data) yield data
      }
    }
  } finally {
    reader.releaseLock()
  }
}

/**
 * AI 图表类型推荐（同步）。
 */
export function recommendChart(request: AiChartRecommendRequestDTO, signal?: AbortSignal) {
  return unwrapResponse<ChartRecommendationDTO[]>(
    client.post('/api/v1/ai/chart/recommend', request, { signal }),
  )
}

/**
 * AI 工作流自动构建（同步）。
 */
export function buildWorkflowDraft(request: AiWorkflowBuildRequestDTO) {
  return unwrapResponse<WorkflowDefinitionDTO>(
    client.post('/api/v1/ai/workflow/build', request),
  )
}

/**
 * AI 通用对话（SSE 流式）。
 * 调用 /api/v1/ai/chat，接收 prompt 和可选历史。
 */
export async function* streamChat(
  prompt: string,
  signal?: AbortSignal,
  history?: Array<{ role: string; content: string }>,
): AsyncIterable<string> {
  const response = await fetch('/api/v1/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...requestContextHeaders,
    },
    body: JSON.stringify({ prompt, history }),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`AI chat request failed: ${response.status} ${response.statusText}`)
  }

  const decoder = new TextDecoder()
  const reader = response.body.getReader()
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() ?? ''
      for (const block of blocks) {
        const lines = block.split('\n')
        const eventLine = lines.find(l => l.startsWith('event:'))
        const dataLine = lines.find(l => l.startsWith('data:'))
        const eventName = eventLine ? eventLine.slice('event:'.length).trim() : 'token'
        const data = dataLine ? dataLine.slice('data:'.length).trim() : ''
        if (eventName === 'error') throw new Error(data || 'AI chat error')
        if (eventName === 'done') return
        if (eventName === 'token' && data) yield data
      }
    }
  }
  finally {
    reader.releaseLock()
  }
}
