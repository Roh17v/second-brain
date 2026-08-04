import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { apiRequest } from '../api/client'
import type { AuthResponse } from '../api/types'

type AuthState = {
  token: string | null
  userId: string | null
  email: string | null
  name: string | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, name: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

const STORAGE_KEY = 'secondbrain.auth'

type StoredAuth = {
  token: string
  userId: string
  email: string
  name: string
}

function loadStored(): StoredAuth | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StoredAuth) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const initial = loadStored()
  const [token, setToken] = useState<string | null>(initial?.token ?? null)
  const [userId, setUserId] = useState<string | null>(initial?.userId ?? null)
  const [email, setEmail] = useState<string | null>(initial?.email ?? null)
  const [name, setName] = useState<string | null>(initial?.name ?? null)

  const persist = useCallback((auth: AuthResponse) => {
    const stored: StoredAuth = {
      token: auth.accessToken,
      userId: auth.userId,
      email: auth.email,
      name: auth.name,
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored))
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
      persist(auth)
    },
    [persist],
  )

  const register = useCallback(
    async (emailValue: string, nameValue: string, password: string) => {
      const auth = await apiRequest<AuthResponse>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          email: emailValue,
          name: nameValue,
          password,
        }),
      })
      persist(auth)
    },
    [persist],
  )

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    setToken(null)
    setUserId(null)
    setEmail(null)
    setName(null)
  }, [])

  const value = useMemo(
    () => ({ token, userId, email, name, login, register, logout }),
    [token, userId, email, name, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
