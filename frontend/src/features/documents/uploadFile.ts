import { api } from '@/api/client'

/**
 * 预签名直传对象存储并完成校验。
 *
 * @param documentId 文档标识
 * @param file 本地文件
 */
export async function uploadDocumentFile(documentId: string, file: File): Promise<void> {
  const checksum = await sha256(file)
  const created = await api<{ uploadId: string; presignedPutUrl: string }>('/api/uploads', {
    method: 'POST',
    body: JSON.stringify({
      documentId,
      filename: file.name,
      sizeBytes: file.size,
      contentType: file.type || 'text/plain',
      checksumSha256: checksum,
    }),
  })
  let put: Response
  try {
    put = await fetch(created.presignedPutUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: file,
    })
  } catch {
    throw new Error('对象存储不可达，请确认 Silo 已启动')
  }
  if (!put.ok) {
    throw new Error(`对象存储上传失败 (${put.status})`)
  }
  await api(`/api/uploads/${created.uploadId}/complete`, { method: 'POST' })
}

async function sha256(file: File): Promise<string> {
  const buffer = await file.arrayBuffer()
  const hash = await crypto.subtle.digest('SHA-256', buffer)
  return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, '0')).join('')
}
