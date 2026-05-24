import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '@/api/client'
import {
  buildWorkflow,
  buildWorkflowDraft,
  dryRunWorkflow,
  executeWorkflow,
  generateSqlStream,
  loadWorkflow,
  saveWorkflow,
} from '@/api/ai'

vi.mock('@/api/client', () => ({
  default: {
    post: vi.fn(),
  },
  requestContextHeaders: {},
  unwrapResponse: <T>(promise: Promise<{ data: { data: T } }>) => promise.then(response => response.data.data),
}))

function createStreamResponse(chunks: string[]) {
  const encoder = new TextEncoder()
  let index = 0
  return {
    ok: true,
    status: 200,
    statusText: 'OK',
    body: {
      getReader() {
        return {
          async read() {
            if (index >= chunks.length) {
              return { done: true, value: undefined }
            }
            const value = encoder.encode(chunks[index])
            index += 1
            return { done: false, value }
          },
          releaseLock() {},
        }
      },
    },
  }
}

describe('generateSqlStream', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('yields token events and passes done payload', async () => {
    const onDone = vi.fn()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(createStreamResponse([
      'event: token\ndata: SELECT \n\n',
      'event: token\ndata: 1\n\n',
      'event: done\ndata: {"conversationId":"conv-1"}\n\n',
    ])))

    const tokens: string[] = []
    for await (const token of generateSqlStream({
      datasourceId: 'ds-1',
      tableName: 'orders',
      description: 'test',
    }, undefined, onDone)) {
      tokens.push(token)
    }

    expect(tokens).toEqual(['SELECT', '1'])
    expect(onDone).toHaveBeenCalledWith({ conversationId: 'conv-1' })
  })

  it('throws when receiving error event', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(createStreamResponse([
      'event: error\ndata: boom\n\n',
    ])))

    const iterate = async () => {
      for await (const _token of generateSqlStream({
        datasourceId: 'ds-1',
        tableName: 'orders',
        description: 'test',
      })) {
      }
    }

    await expect(iterate()).rejects.toThrow('boom')
  })
})

describe('workflow build api', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('uses legacy draft response mode by default', async () => {
    vi.mocked(client.post).mockResolvedValue({
      data: {
        data: {
          workflowId: 'wf-1',
          workflowName: 'draft',
          nodes: [],
          edges: [],
          positions: {},
        },
      },
    })

    const result = await buildWorkflowDraft({
      datasourceId: 'ds-1',
      description: 'draft me',
    })

    expect(client.post).toHaveBeenCalledWith('/api/v1/ai/workflow/build', {
      datasourceId: 'ds-1',
      description: 'draft me',
      buildMode: 'DRAFT_ONLY',
      responseMode: 'LEGACY_DRAFT',
    })
    expect(result.workflowId).toBe('wf-1')
  })

  it('uses envelope response mode for agent build', async () => {
    vi.mocked(client.post).mockResolvedValue({
      data: {
        data: {
          responseType: 'DRAFT',
          buildMode: 'AGENT',
          workflowId: 'wf-2',
          draft: {
            workflowId: 'wf-2',
            workflowName: 'agent draft',
            nodes: [],
            edges: [],
            positions: {},
          },
        },
      },
    })

    const result = await buildWorkflow({
      datasourceId: 'ds-1',
      description: 'agent me',
      buildMode: 'AGENT',
    })

    expect(client.post).toHaveBeenCalledWith('/api/v1/ai/workflow/build', {
      datasourceId: 'ds-1',
      description: 'agent me',
      buildMode: 'AGENT',
      responseMode: 'ENVELOPE',
    })
    expect(result.responseType).toBe('DRAFT')
    expect(result.draft?.workflowName).toBe('agent draft')
  })

  it('passes clarification and continuation fields for workflow build', async () => {
    vi.mocked(client.post).mockResolvedValue({
      data: {
        data: {
          responseType: 'TASK_ACCEPTED',
          buildMode: 'RUN_AND_SAVE',
          agentTaskId: 'agent-1',
        },
      },
    })

    const result = await buildWorkflow({
      datasourceId: 'ds-1',
      description: 'continue build',
      buildMode: 'RUN_AND_SAVE',
      conversationId: 'conv-1',
      agentTaskId: 'agent-0',
      clarificationAnswers: [{ key: 'timeRange', value: '7d' }],
      runAndSave: true,
    })

    expect(client.post).toHaveBeenCalledWith('/api/v1/ai/workflow/build', {
      datasourceId: 'ds-1',
      description: 'continue build',
      buildMode: 'RUN_AND_SAVE',
      conversationId: 'conv-1',
      agentTaskId: 'agent-0',
      clarificationAnswers: [{ key: 'timeRange', value: '7d' }],
      runAndSave: true,
      responseMode: 'ENVELOPE',
    })
    expect(result.agentTaskId).toBe('agent-1')
  })

  it('uses envelope response mode for run and save build', async () => {
    vi.mocked(client.post).mockResolvedValue({
      data: {
        data: {
          responseType: 'DRAFT',
          buildMode: 'RUN_AND_SAVE',
          workflowId: 'wf-3',
          datasetId: 'ds-saved',
          saved: true,
          execution: {
            supported: true,
            status: 'SUCCEEDED',
            workflowId: 'wf-3',
            finalResultNodeId: 'node-output',
          },
          draft: {
            workflowId: 'wf-3',
            workflowName: 'saved draft',
            nodes: [],
            edges: [],
            positions: {},
          },
        },
      },
    })

    const result = await buildWorkflow({
      datasourceId: 'ds-1',
      description: 'save me',
      buildMode: 'RUN_AND_SAVE',
    })

    expect(client.post).toHaveBeenCalledWith('/api/v1/ai/workflow/build', {
      datasourceId: 'ds-1',
      description: 'save me',
      buildMode: 'RUN_AND_SAVE',
      responseMode: 'ENVELOPE',
    })
    expect(result.saved).toBe(true)
    expect(result.workflowId).toBe('wf-3')
    expect(result.datasetId).toBe('ds-saved')
    expect(result.execution?.status).toBe('SUCCEEDED')
    expect(result.draft?.workflowName).toBe('saved draft')
  })
})

describe('workflow ai helpers', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('calls save workflow api', async () => {
    vi.mocked(client.post).mockResolvedValue({
      data: {
        data: {
          workflowId: 'wf-save',
          versionId: 'v1',
        },
      },
    })

    await saveWorkflow({ workflowId: 'wf-save' })

    expect(client.post).toHaveBeenCalledWith('/api/v1/ai/workflow/save', { workflowId: 'wf-save' })
  })

  it('calls load workflow api', async () => {
    vi.mocked(client.post).mockResolvedValue({
      data: {
        data: {
          workflowId: 'wf-load',
        },
      },
    })

    await loadWorkflow('wf-load')

    expect(client.post).toHaveBeenCalledWith('/api/v1/ai/workflow/load', { workflowId: 'wf-load' })
  })

  it('calls execute workflow api', async () => {
    vi.mocked(client.post).mockResolvedValue({ data: { data: { supported: true } } })

    await executeWorkflow({ workflowId: 'wf-run' })

    expect(client.post).toHaveBeenCalledWith('/api/v1/ai/workflow/execute', { workflowId: 'wf-run' })
  })

  it('calls dry run workflow api', async () => {
    vi.mocked(client.post).mockResolvedValue({ data: { data: { valid: true } } })

    await dryRunWorkflow({ workflowId: 'wf-run' })

    expect(client.post).toHaveBeenCalledWith('/api/v1/ai/workflow/dry-run', { workflowId: 'wf-run' })
  })
})
