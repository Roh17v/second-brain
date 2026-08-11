const API_BASE = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

/** Must match AuthContext storage key. */
export const AUTH_STORAGE_KEY = 'secondbrain.auth'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

type UnauthorizedHandler = () => void
const unauthorizedHandlers = new Set<UnauthorizedHandler>()

/** Subscribe to session expiry / 401 on protected APIs. */
export function onUnauthorized(handler: UnauthorizedHandler): () => void {
  unauthorizedHandlers.add(handler)
  return () => unauthorizedHandlers.delete(handler)
}

/**
 * Notify the app that the session is invalid.
 * Does not hard-redirect — AuthProvider shows a notice then navigates smoothly.
 */
export function handleUnauthorized() {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY)
  } catch {
    // ignore
  }
  for (const handler of unauthorizedHandlers) {
    try {
      handler()
    } catch {
      // ignore
    }
  }
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  token?: string | null,
): Promise<T> {
  const headers = new Headers(options.headers)

  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  })

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  let data: { message?: string; error?: string } | null = null
  if (text) {
    try {
      data = JSON.parse(text) as { message?: string; error?: string }
    } catch {
      data = null
    }
  }

  if (!response.ok) {
    // Failed login/register/google/OTP must not trigger the session-expired overlay
    const isAuthEndpoint = path.startsWith('/api/auth/')
    if (response.status === 401 && !isAuthEndpoint) {
      handleUnauthorized()
    }
    const message =
      data?.message || data?.error || response.statusText || 'Request failed'
    throw new ApiError(response.status, message)
  }

  return data as T
}
