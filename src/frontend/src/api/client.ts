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
  return Promise.reject(error)
})

export async function unwrapResponse<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await promise
  return response.data.data
}

export default client
