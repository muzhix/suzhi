import { api } from '@/api/client'

export interface Job {
  jobId: string
  type: string
  status: string
  stage?: string
  progress?: number
  total?: number
  documentVersionId?: string | null
  errorSummary?: string | null
}

export function isJobTerminal(status?: string): boolean {
  return Boolean(status && ['succeeded', 'failed', 'cancelled'].includes(status))
}

/**
 * 任务状态的界面文案。
 *
 * @param status 任务状态
 */
export function jobStatusLabel(status?: string): string {
  switch (status) {
    case 'queued':
      return '排队中'
    case 'running':
      return '进行中'
    case 'succeeded':
      return '已完成'
    case 'failed':
      return '失败'
    case 'cancelled':
      return '已取消'
    default:
      return status || '处理中'
  }
}

/**
 * 提交任务。不携带客户端幂等键；同参数任务在服务端去重，终态后重跑由服务端按序号新建。
 *
 * @param path 任务接口
 * @param body 可选 JSON
 */
export function startJob(path: string, body?: unknown): Promise<Job> {
  return api<Job>(path, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}
