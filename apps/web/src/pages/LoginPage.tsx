import { FormEvent, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { Brain, Loader2 } from 'lucide-react'
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
import {
  AuthDivider,
  GoogleSignInButton,
} from '@/components/auth/GoogleSignInButton'

function safeNextPath(raw: string | null): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//')) return '/'
  if (raw.startsWith('/login') || raw.startsWith('/register')) return '/'
  return raw
}

export default function LoginPage() {
  const { login, token } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [email, setEmail] = useState(() => searchParams.get('email')?.trim() ?? '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const nextPath = useMemo(
    () => safeNextPath(searchParams.get('next')),
    [searchParams],
  )
  const sessionExpired = searchParams.get('reason') === 'session'
  const passwordResetOk = searchParams.get('reset') === '1'

  /** Only after login fails because email is not verified yet. */
  const needsVerification =
    !!error && /verif(y|ication)|email code|inbox for a code/i.test(error)

  const noAccount =
    !!error && /no account exists for this email/i.test(error)

  if (token) return <Navigate to={nextPath} replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(email, password)
      navigate(nextPath)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md shadow-lift">
        <CardHeader className="space-y-3 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-soft">
            <Brain className="h-6 w-6" />
          </div>
          <CardTitle className="text-2xl tracking-tight">Welcome back</CardTitle>
          <CardDescription>
            Sign in to continue
          </CardDescription>
        </CardHeader>
        <CardContent>
          {sessionExpired && !error && (
            <div className="mb-4 rounded-xl border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-sm text-amber-900 dark:text-amber-100">
              Your session expired. Please sign in again.
            </div>
          )}
          {passwordResetOk && !error && (
            <div className="mb-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-900 dark:text-emerald-100">
              Password updated. Sign in with your email and new password.
            </div>
          )}
          {error && (
            <div className="mb-4 rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
              {noAccount && (
                <>
                  {' '}
                  <Link
                    to="/register"
                    className="font-medium underline underline-offset-2"
                  >
                    Create an account
                  </Link>
                </>
              )}
            </div>
          )}
          <GoogleSignInButton
            disabled={loading}
            onSuccess={() => navigate(nextPath)}
            onError={(message) => setError(message)}
          />
          <AuthDivider />
          <form className="space-y-4" onSubmit={onSubmit}>
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
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-2">
                <Label htmlFor="password">Password</Label>
                <Link
                  to={
                    email.trim()
                      ? `/forgot-password?email=${encodeURIComponent(email.trim())}`
                      : '/forgot-password'
                  }
                  className="text-xs font-medium text-primary hover:underline"
                >
                  Forgot password?
                </Link>
              </div>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
              />
            </div>
            <Button className="w-full" type="submit" disabled={loading}>
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              {loading ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
          {needsVerification && (
            <p className="mt-4 text-center text-sm text-muted-foreground">
              Have a verification code?{' '}
              <Link
                to={
                  email.trim()
                    ? `/verify-email?email=${encodeURIComponent(email.trim())}`
                    : '/verify-email'
                }
                className="font-medium text-primary hover:underline"
              >
                Enter email code
              </Link>
            </p>
          )}
          <p
            className={`text-center text-sm text-muted-foreground ${
              needsVerification ? 'mt-2' : 'mt-4'
            }`}
          >
            No account?{' '}
            <Link to="/register" className="font-medium text-primary hover:underline">
              Create one
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
