import client, { unwrapResponse } from './client'
import type { ApprovalDecideRequestDTO, ApprovalRequestDTO, ApprovalStatus, PageResult } from '@/types/contract'

export function listApprovals(params: {
  page?: number
  pageSize?: number
  status?: ApprovalStatus
  workflowId?: string
} = {}) {
  return unwrapResponse<PageResult<ApprovalRequestDTO>>(
    client.get('/api/v1/approvals', { params }),
  )
}

export function getApproval(requestId: string) {
  return unwrapResponse<ApprovalRequestDTO>(client.get(`/api/v1/approvals/${requestId}`))
}

export function approveRequest(requestId: string, payload: ApprovalDecideRequestDTO = {}) {
  return unwrapResponse<ApprovalRequestDTO>(
    client.post(`/api/v1/approvals/${requestId}/approve`, payload),
  )
}

export function rejectRequest(requestId: string, payload: ApprovalDecideRequestDTO = {}) {
  return unwrapResponse<ApprovalRequestDTO>(
    client.post(`/api/v1/approvals/${requestId}/reject`, payload),
  )
}
