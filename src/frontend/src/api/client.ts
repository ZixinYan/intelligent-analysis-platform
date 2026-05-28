import axios from 'axios'
import type { ApiResponse } from '@/types/contract'

export const requestContextHeaders = {
  'X-Tenant-Id': 'demo-tenant',
  'X-User-Id': 'demo-user',
}

const client = axios.create({
  baseURL: '/',
  timeout: 15000,
  headers: {
    ...requestContextHeaders,
  },
})

client.interceptors.response.use((response) => response, (error) => {
  // Extract error message from API response
  if (error.response?.data?.message) {
    const apiError = new Error(error.response.data.message)
    apiError.name = error.response.data.code || 'ApiError'
    return Promise.reject(apiError)
  }
  return Promise.reject(error)
})

export async function unwrapResponse<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await promise
  return response.data.data
}

export default client
