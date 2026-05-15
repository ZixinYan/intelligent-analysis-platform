import client, { unwrapResponse } from './client'
import type { ExportFileDTO, TriggerExportRequestDTO } from '@/types/contract'

export function triggerExport(payload: TriggerExportRequestDTO) {
  return unwrapResponse<ExportFileDTO>(client.post('/api/v1/exports', payload))
}

export function getExportFile(fileId: string) {
  return unwrapResponse<ExportFileDTO>(client.get(`/api/v1/exports/${fileId}`))
}

export function getExportDownloadUrl(fileId: string) {
  return unwrapResponse<{ url: string }>(client.get(`/api/v1/exports/${fileId}/download-url`))
}

/** 轮询直到文件就绪（fileSizeBytes 非 null），最长等待 60s */
export function waitForExport(fileId: string, intervalMs = 2000): Promise<ExportFileDTO> {
  const deadline = Date.now() + 60_000
  return new Promise((resolve, reject) => {
    const tick = async () => {
      if (Date.now() > deadline)
        return reject(new Error('导出超时，请稍后重试'))
      try {
        const file = await getExportFile(fileId)
        if (file.fileSizeBytes != null)
          return resolve(file)
        setTimeout(tick, intervalMs)
      }
      catch (err) {
        reject(err)
      }
    }
    tick()
  })
}

/** 通过下载 URL 触发浏览器下载 */
export async function downloadExportFile(fileId: string, fileName: string) {
  const { url } = await getExportDownloadUrl(fileId)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
}
