import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { apiRequest, AUTH_STORAGE_KEY, onUnauthorized } from '../api/client'
import type { AuthResponse, RegisterResponse } from '../api/types'
import { SessionExpiredOverlay } from '../components/auth/SessionExpiredOverlay'

type AuthState = {
  token: string | null
  userId: string | null
  email: string | null
  name: string | null
  /** True while the “session expired” overlay is visible. */
  sessionExpired: boolean
  login: (email: string, password: string) => Promise<void>
  /**
   * Creates account and sends OTP. Returns whether email verification is required
   * (caller should navigate to /verify-email). Does not set session until verified.
   */
  register: (
    email: string,
    name: string,
    password: string,
  ) => Promise<{ verificationRequired: boolean; email: string }>
  /**
   * Continue with Google: exchange GIS ID token or OAuth access token for our JWT.
   */
  loginWithGoogle: (token: {
    idToken?: string
    accessToken?: string
  }) => Promise<void>
  /** Persist session after OTP verification (or legacy AUTHENTICATED register). */
  completeAuth: (auth: AuthResponse) => void
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

type StoredAuth = {
  token: string
  userId: string
  email: string
  name: string
}

/** Clock skew before treating JWT as expired (ms). */
const EXPIRY_SKEW_MS = 5_000

/** True if JWT is missing, malformed, or past exp. */
export function isAccessTokenExpired(token: string | null | undefined): boolean {
  if (!token) return true
  try {
    const parts = token.split('.')
    if (parts.length < 2) return true
    const json = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
    const payload = JSON.parse(json) as { exp?: number }
    if (typeof payload.exp !== 'number') return false
    return Date.now() >= payload.exp * 1000 - EXPIRY_SKEW_MS
  } catch {
    return true
  }
}

function loadStored(): { auth: StoredAuth | null; wasExpired: boolean } {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return { auth: null, wasExpired: false }
    const stored = JSON.parse(raw) as StoredAuth
    if (!stored?.token) {
      localStorage.removeItem(AUTH_STORAGE_KEY)
      return { auth: null, wasExpired: false }
    }
    if (isAccessTokenExpired(stored.token)) {
      localStorage.removeItem(AUTH_STORAGE_KEY)
      return { auth: null, wasExpired: true }
    }
    return { auth: stored, wasExpired: false }
  } catch {
    return { auth: null, wasExpired: false }
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const returnPathRef = useRef(`${location.pathname}${location.search}`)

  const initial = loadStored()
  const [token, setToken] = useState<string | null>(initial.auth?.token ?? null)
  const [userId, setUserId] = useState<string | null>(initial.auth?.userId ?? null)
  const [email, setEmail] = useState<string | null>(initial.auth?.email ?? null)
  const [name, setName] = useState<string | null>(initial.auth?.name ?? null)
  const [sessionExpired, setSessionExpired] = useState(() => {
    if (!initial.wasExpired) return false
    const p = window.location.pathname
    return p !== '/login' && p !== '/register'
  })
  const handlingRef = useRef(sessionExpired)

  // Keep latest path for post-login return (avoid stale closure in timers)
  useEffect(() => {
    if (!sessionExpired) {
      returnPathRef.current = `${location.pathname}${location.search}`
    }
  }, [location.pathname, location.search, sessionExpired])

  const clearAuthState = useCallback(() => {
    localStorage.removeItem(AUTH_STORAGE_KEY)
    setToken(null)
    setUserId(null)
    setEmail(null)
    setName(null)
  }, [])

  const beginSessionExpired = useCallback(() => {
    if (handlingRef.current) return
    const path = returnPathRef.current || '/'
    // Already on auth pages — just clear, no overlay
    if (path.startsWith('/login') || path.startsWith('/register')) {
      clearAuthState()
      setSessionExpired(false)
      return
    }
    handlingRef.current = true
    clearAuthState()
    setSessionExpired(true)
  }, [clearAuthState])

  const finishSessionExpired = useCallback(() => {
    const next = returnPathRef.current || '/'
    setSessionExpired(false)
    handlingRef.current = false
    navigate(
      `/login?reason=session&next=${encodeURIComponent(next)}`,
      { replace: true },
    )
  }, [navigate])

  const logout = useCallback(() => {
    handlingRef.current = false
    setSessionExpired(false)
    clearAuthState()
  }, [clearAuthState])

  // API 401 → soft session-expired flow
  useEffect(() => {
    return onUnauthorized(() => {
      beginSessionExpired()
    })
  }, [beginSessionExpired])

  // Proactive JWT exp while tab stays open
  useEffect(() => {
    if (!token || sessionExpired) return
    try {
      const parts = token.split('.')
      const json = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
      const payload = JSON.parse(json) as { exp?: number }
      if (typeof payload.exp !== 'number') return
      const ms = payload.exp * 1000 - Date.now() - EXPIRY_SKEW_MS
      if (ms <= 0) {
        beginSessionExpired()
        return
      }
      const id = window.setTimeout(() => beginSessionExpired(), ms)
      return () => window.clearTimeout(id)
    } catch {
      beginSessionExpired()
    }
  }, [token, sessionExpired, beginSessionExpired])

  const completeAuth = useCallback((auth: AuthResponse) => {
    handlingRef.current = false
    setSessionExpired(false)
    const stored: StoredAuth = {
      token: auth.accessToken,
      userId: auth.userId,
      email: auth.email,
      name: auth.name,
    }
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(stored))
    setToken(stored.token)
    setUserId(stored.userId)
    setEmail(stored.email)
    setName(stored.name)
  }, [])

  const login = useCallback(
    async (emailValue: string, password: string) => {
      const auth = await apiRequest<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email: emailValue, password }),
      })
      completeAuth(auth)
    },
    [completeAuth],
  )

  const loginWithGoogle = useCallback(
    async (token: { idToken?: string; accessToken?: string }) => {
      const auth = await apiRequest<AuthResponse>('/api/auth/google', {
        method: 'POST',
        body: JSON.stringify({
          idToken: token.idToken || undefined,
          accessToken: token.accessToken || undefined,
        }),
      })
      completeAuth(auth)
    },
    [completeAuth],
  )

  const register = useCallback(
    async (emailValue: string, nameValue: string, password: string) => {
      const res = await apiRequest<RegisterResponse>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          email: emailValue,
          name: nameValue,
          password,
        }),
      })
      if (res.status === 'AUTHENTICATED' && res.auth) {
        completeAuth(res.auth)
        return { verificationRequired: false, email: res.email }
      }
      return {
        verificationRequired: true,
        email: res.email || emailValue,
      }
    },
    [completeAuth],
  )

  const value = useMemo(
    () => ({
      token,
      userId,
      email,
      name,
      sessionExpired,
      login,
      loginWithGoogle,
      register,
      completeAuth,
      logout,
    }),
    [
      token,
      userId,
      email,
      name,
      sessionExpired,
      login,
      loginWithGoogle,
      register,
      completeAuth,
      logout,
    ],
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
      {sessionExpired && (
        <SessionExpiredOverlay onContinue={finishSessionExpired} />
      )}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
