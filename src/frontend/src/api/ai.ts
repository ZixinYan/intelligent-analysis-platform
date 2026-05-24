import client, { requestContextHeaders, unwrapResponse } from './client'
import type {
  AiChartRecommendRequestDTO,
  AiSqlRequestDTO,
  AiWorkflowBuildRequestDTO,
  AiWorkflowBuildResultDTO,
  AiWorkflowDryRunRequestDTO,
  AiWorkflowDryRunResultDTO,
  AiWorkflowExecuteRequestDTO,
  AiWorkflowExecuteResultDTO,
  AiWorkflowLoadResultDTO,
  AiWorkflowSaveRequestDTO,
  AiWorkflowSaveResultDTO,
  ChartRecommendationDTO,
  WorkflowDefinitionDTO,
} from '@/types/contract'

export async function* generateSqlStream(
  request: AiSqlRequestDTO,
  signal?: AbortSignal,
  onDone?: (payload: { conversationId?: string }) => void,
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
        if (eventName === 'done') {
          onDone?.(parseDonePayload(data))
          return
        }
        if (eventName === 'token' && data) yield data
      }
    }
  } finally {
    reader.releaseLock()
  }
}

export function recommendChart(request: AiChartRecommendRequestDTO, signal?: AbortSignal) {
  return unwrapResponse<ChartRecommendationDTO[]>(
    client.post('/api/v1/ai/chart/recommend', request, { signal }),
  )
}

export function buildWorkflowDraft(request: AiWorkflowBuildRequestDTO) {
  return unwrapResponse<WorkflowDefinitionDTO>(
    client.post('/api/v1/ai/workflow/build', {
      ...request,
      buildMode: request.buildMode ?? 'DRAFT_ONLY',
      responseMode: 'LEGACY_DRAFT',
    }),
  )
}

export function buildWorkflow(request: AiWorkflowBuildRequestDTO) {
  return unwrapResponse<AiWorkflowBuildResultDTO>(
    client.post('/api/v1/ai/workflow/build', {
      ...request,
      buildMode: request.buildMode ?? 'DRAFT_ONLY',
      responseMode: 'ENVELOPE',
    }),
  )
}

export function saveWorkflow(request: AiWorkflowSaveRequestDTO) {
  return unwrapResponse<AiWorkflowSaveResultDTO>(
    client.post('/api/v1/ai/workflow/save', request),
  )
}

export function loadWorkflow(workflowId: string) {
  return unwrapResponse<AiWorkflowLoadResultDTO>(
    client.post('/api/v1/ai/workflow/load', { workflowId }),
  )
}

export function executeWorkflow(request: AiWorkflowExecuteRequestDTO) {
  return unwrapResponse<AiWorkflowExecuteResultDTO>(
    client.post('/api/v1/ai/workflow/execute', request),
  )
}

export function dryRunWorkflow(request: AiWorkflowDryRunRequestDTO) {
  return unwrapResponse<AiWorkflowDryRunResultDTO>(
    client.post('/api/v1/ai/workflow/dry-run', request),
  )
}

interface ChatHistoryItem {
  role: string
  content: string
}

interface StreamChatOptions {
  prompt: string
  conversationId?: string
  signal?: AbortSignal
  history?: ChatHistoryItem[]
  onDone?: (payload: { conversationId?: string }) => void
}

export async function* streamChat({
  prompt,
  conversationId,
  signal,
  history,
  onDone,
}: StreamChatOptions): AsyncIterable<string> {
  const response = await fetch('/api/v1/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...requestContextHeaders,
    },
    body: JSON.stringify({ prompt, conversationId, history }),
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
        if (eventName === 'done') {
          onDone?.(parseDonePayload(data))
          return
        }
        if (eventName === 'token' && data) yield data
      }
    }
  }
  finally {
    reader.releaseLock()
  }
}

function parseDonePayload(data: string): { conversationId?: string } {
  if (!data) return {}
  try {
    const parsed = JSON.parse(data) as { conversationId?: string }
    return parsed && typeof parsed.conversationId === 'string'
      ? { conversationId: parsed.conversationId }
      : {}
  }
  catch {
    return {}
  }
}
