import { FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { Brain, Loader2, Mail } from 'lucide-react'
import { apiRequest } from '@/api/client'
import type { AuthResponse } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

/** Matches backend default EMAIL_RESEND_COOLDOWN_SECONDS */
const DEFAULT_RESEND_COOLDOWN_SEC = 60

function parseWaitSeconds(message: string): number | null {
  const m = message.match(/wait\s+(\d+)\s+seconds?/i)
  if (!m) return null
  const n = Number(m[1])
  return Number.isFinite(n) && n > 0 ? n : null
}

export default function VerifyEmailPage() {
  const { token, completeAuth } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const emailFromQuery = params.get('email')?.trim() ?? ''

  const [email, setEmail] = useState(emailFromQuery)
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [verifying, setVerifying] = useState(false)
  const [resending, setResending] = useState(false)
  // If they just registered, a code was already sent → start cooldown immediately
  const [cooldownSec, setCooldownSec] = useState(() =>
    emailFromQuery ? DEFAULT_RESEND_COOLDOWN_SEC : 0,
  )

  const maskedHint = useMemo(() => {
    if (!email || !email.includes('@')) return null
    const [local, domain] = email.split('@')
    if (local.length <= 2) return `**@${domain}`
    return `${local[0]}***${local[local.length - 1]}@${domain}`
  }, [email])

  useEffect(() => {
    if (cooldownSec <= 0) return
    const id = window.setTimeout(() => {
      setCooldownSec((s) => (s <= 1 ? 0 : s - 1))
    }, 1000)
    return () => window.clearTimeout(id)
  }, [cooldownSec])

  if (token) return <Navigate to="/" replace />

  async function onVerify(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setInfo(null)
    setVerifying(true)
    try {
      const auth = await apiRequest<AuthResponse>('/api/auth/verify-email', {
        method: 'POST',
        body: JSON.stringify({ email: email.trim(), code: code.trim() }),
      })
      completeAuth(auth)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Verification failed')
    } finally {
      setVerifying(false)
    }
  }

  async function onResend() {
    if (cooldownSec > 0 || resending || !email.trim()) return
    setError(null)
    setInfo(null)
    setResending(true)
    try {
      const res = await apiRequest<{ message: string }>(
        '/api/auth/resend-verification',
        {
          method: 'POST',
          body: JSON.stringify({ email: email.trim() }),
        },
      )
      setInfo(res.message || 'If an account exists, a new code was sent.')
      setCooldownSec(DEFAULT_RESEND_COOLDOWN_SEC)
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Could not resend code'
      const wait = parseWaitSeconds(message)
      if (wait != null) {
        setCooldownSec(wait)
        setInfo(message)
        setError(null)
      } else {
        setError(message)
      }
    } finally {
      setResending(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md shadow-lift">
        <CardHeader className="space-y-3 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-soft">
            <Mail className="h-6 w-6" />
          </div>
          <CardTitle className="text-2xl tracking-tight">Check your email</CardTitle>
          <CardDescription>
            Enter the 6-digit code we sent
            {maskedHint ? (
              <>
                {' '}
                to <span className="font-medium text-foreground">{maskedHint}</span>
              </>
            ) : (
              ' to your inbox'
            )}
            .
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onVerify}>
            {error && (
              <div className="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {error}
              </div>
            )}
            {info && (
              <div className="rounded-xl border border-border bg-muted/50 px-3 py-2 text-sm text-muted-foreground">
                {info}
              </div>
            )}
            {!emailFromQuery && (
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                />
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="code">Verification code</Label>
              <Input
                id="code"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="000000"
                value={code}
                onChange={(e) =>
                  setCode(e.target.value.replace(/\D/g, '').slice(0, 6))
                }
                required
                minLength={6}
                maxLength={6}
                className="text-center text-lg tracking-[0.4em] font-semibold"
              />
            </div>
            <Button className="w-full" type="submit" disabled={verifying || code.length !== 6}>
              {verifying && <Loader2 className="h-4 w-4 animate-spin" />}
              {verifying ? 'Verifying…' : 'Verify and continue'}
            </Button>
          </form>
          <div className="mt-4 flex flex-col items-center gap-2 text-sm">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={resending || cooldownSec > 0 || !email.trim()}
              onClick={() => void onResend()}
            >
              {resending && <Loader2 className="h-4 w-4 animate-spin" />}
              {cooldownSec > 0
                ? `Resend code in ${cooldownSec}s`
                : resending
                  ? 'Sending…'
                  : 'Resend code'}
            </Button>
            {cooldownSec > 0 && (
              <p className="text-xs text-muted-foreground">
                You can request a new code when the timer ends.
              </p>
            )}
            <p className="text-muted-foreground">
              Wrong email?{' '}
              <Link to="/register" className="font-medium text-primary hover:underline">
                Sign up again
              </Link>
            </p>
          </div>
          <p className="mt-4 flex items-center justify-center gap-1.5 text-xs text-muted-foreground">
            <Brain className="h-3.5 w-3.5" />
            SecondBrain · codes expire in 10 minutes
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
