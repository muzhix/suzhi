export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const csrf = await ensureCsrf()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (csrf && init.method && init.method !== 'GET') {
    headers.set('X-XSRF-TOKEN', csrf)
  }
  const response = await fetch(path, { ...init, headers, credentials: 'include' })
  if (response.status === 401) {
    throw new ApiError(401, '未登录')
  }
  if (!response.ok) {
    let detail = response.statusText
    try {
      const body = (await response.json()) as { detail?: string; title?: string }
      detail = body.detail || body.title || detail
    } catch {
      // ignore
    }
    throw new ApiError(response.status, detail)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function ensureCsrf(): Promise<string> {
  const fromCookie = readCookie('XSRF-TOKEN')
  if (fromCookie) {
    return decodeURIComponent(fromCookie)
  }
  const body = await fetch('/api/auth/csrf', { credentials: 'include' }).then(
    (response) => response.json() as Promise<{ token: string }>,
  )
  return body.token
}

function readCookie(name: string): string | undefined {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match?.[1]
}
