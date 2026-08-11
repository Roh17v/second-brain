import { FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { Brain, KeyRound, Loader2 } from 'lucide-react'
import { apiRequest } from '@/api/client'
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

const DEFAULT_RESEND_COOLDOWN_SEC = 60

function parseWaitSeconds(message: string): number | null {
  const m = message.match(/wait\s+(\d+)\s+seconds?/i)
  if (!m) return null
  const n = Number(m[1])
  return Number.isFinite(n) && n > 0 ? n : null
}

type Step = 'email' | 'reset'

/**
 * Forgot password + set password for Google-only accounts (same OTP flow).
 */
export default function ForgotPasswordPage() {
  const { token } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const emailFromQuery = params.get('email')?.trim() ?? ''
  const modeHint = params.get('mode') // optional UI hint before server responds

  const [step, setStep] = useState<Step>('email')
  const [email, setEmail] = useState(emailFromQuery)
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [cooldownSec, setCooldownSec] = useState(0)
  /** Server truth: set_password | reset_password | unknown */
  const [intent, setIntent] = useState<string | null>(
    modeHint === 'set' ? 'set_password' : null,
  )

  const isSetPassword = intent === 'set_password'

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

  async function sendCode(e?: FormEvent) {
    e?.preventDefault()
    if (cooldownSec > 0 || sending || !email.trim()) return
    setError(null)
    setInfo(null)
    setSending(true)
    try {
      const res = await apiRequest<{ message: string; intent?: string }>(
        '/api/auth/forgot-password',
        {
          method: 'POST',
          body: JSON.stringify({ email: email.trim() }),
        },
      )
      if (res.intent === 'set_password' || res.intent === 'reset_password') {
        setIntent(res.intent)
      }
      setInfo(
        res.message ||
          (res.intent === 'set_password'
            ? 'We sent a code to set your password.'
            : res.intent === 'reset_password'
              ? 'We sent a code to reset your password.'
              : 'If an account exists, a code was sent.'),
      )
      setStep('reset')
      setCooldownSec(DEFAULT_RESEND_COOLDOWN_SEC)
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not send code'
      const wait = parseWaitSeconds(message)
      if (wait != null) {
        setCooldownSec(wait)
        setInfo(message)
        setStep('reset')
        setError(null)
      } else {
        setError(message)
      }
    } finally {
      setSending(false)
    }
  }

  async function onReset(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setInfo(null)
    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }
    setResetting(true)
    try {
      await apiRequest('/api/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({
          email: email.trim(),
          code: code.trim(),
          newPassword: password,
        }),
      })
      navigate(
        `/login?reset=1&email=${encodeURIComponent(email.trim())}`,
        { replace: true },
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update password')
    } finally {
      setResetting(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md shadow-lift">
        <CardHeader className="space-y-3 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-soft">
            <KeyRound className="h-6 w-6" />
          </div>
          <CardTitle className="text-2xl tracking-tight">
            {isSetPassword
              ? 'Set a password'
              : intent === 'reset_password'
                ? 'Reset password'
                : 'Forgot password'}
          </CardTitle>
          <CardDescription>
            {step === 'email'
              ? isSetPassword
                ? 'We will email a code so you can add a password (e.g. if you signed up with Google).'
                : 'Enter your email. We will send a code to reset or set your password.'
              : (
                <>
                  Enter the code
                  {maskedHint ? (
                    <>
                      {' '}
                      sent to{' '}
                      <span className="font-medium text-foreground">{maskedHint}</span>
                    </>
                  ) : null}{' '}
                  and {isSetPassword ? 'choose a password' : 'choose a new password'}.
                </>
              )}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {error && (
            <div className="mb-4 rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </div>
          )}
          {info && (
            <div className="mb-4 rounded-xl border border-border bg-muted/50 px-3 py-2 text-sm text-muted-foreground">
              {info}
            </div>
          )}

          {step === 'email' ? (
            <form className="space-y-4" onSubmit={(e) => void sendCode(e)}>
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
              <Button className="w-full" type="submit" disabled={sending || !email.trim()}>
                {sending && <Loader2 className="h-4 w-4 animate-spin" />}
                {sending ? 'Sending…' : 'Send code'}
              </Button>
            </form>
          ) : (
            <form className="space-y-4" onSubmit={onReset}>
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
              <div className="space-y-2">
                <Label htmlFor="password">New password</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  minLength={8}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirm">Confirm password</Label>
                <Input
                  id="confirm"
                  type="password"
                  autoComplete="new-password"
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  required
                  minLength={8}
                />
              </div>
              <Button
                className="w-full"
                type="submit"
                disabled={resetting || code.length !== 6}
              >
                {resetting && <Loader2 className="h-4 w-4 animate-spin" />}
                {resetting
                  ? 'Saving…'
                  : isSetPassword
                    ? 'Set password'
                    : 'Reset password'}
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="w-full"
                disabled={sending || cooldownSec > 0}
                onClick={() => void sendCode()}
              >
                {sending && <Loader2 className="h-4 w-4 animate-spin" />}
                {cooldownSec > 0
                  ? `Resend code in ${cooldownSec}s`
                  : 'Resend code'}
              </Button>
            </form>
          )}

          <p className="mt-6 text-center text-sm text-muted-foreground">
            Remembered it?{' '}
            <Link to="/login" className="font-medium text-primary hover:underline">
              Sign in
            </Link>
          </p>
          <p className="mt-4 flex items-center justify-center gap-1.5 text-xs text-muted-foreground">
            <Brain className="h-3.5 w-3.5" />
            SecondBrain · codes expire in 10 minutes
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
