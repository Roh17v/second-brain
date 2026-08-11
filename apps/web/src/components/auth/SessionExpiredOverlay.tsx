import { useEffect, useState } from 'react'
import { LogIn, ShieldAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

const AUTO_REDIRECT_MS = 5000

type Props = {
  onContinue: () => void
}

/**
 * Full-screen notice shown when the JWT expires or the API returns 401.
 * Auto-continues to login after a short delay for a smooth handoff.
 */
export function SessionExpiredOverlay({ onContinue }: Props) {
  const [secondsLeft, setSecondsLeft] = useState(
    Math.ceil(AUTO_REDIRECT_MS / 1000),
  )

  useEffect(() => {
    // Snapshot callback once so a new function identity does not reset the timer
    const continueFn = onContinue
    const started = Date.now()
    setSecondsLeft(Math.ceil(AUTO_REDIRECT_MS / 1000))
    const tick = window.setInterval(() => {
      const left = Math.max(
        0,
        Math.ceil((AUTO_REDIRECT_MS - (Date.now() - started)) / 1000),
      )
      setSecondsLeft(left)
    }, 250)
    const done = window.setTimeout(() => continueFn(), AUTO_REDIRECT_MS)
    return () => {
      window.clearInterval(tick)
      window.clearTimeout(done)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once on mount
  }, [])

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-background/80 p-4 backdrop-blur-sm animate-in fade-in duration-300"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="session-expired-title"
      aria-describedby="session-expired-desc"
    >
      <Card className="w-full max-w-md shadow-lift animate-in fade-in zoom-in-95 duration-300">
        <CardHeader className="space-y-3 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-500/15 text-amber-700 dark:text-amber-300">
            <ShieldAlert className="h-6 w-6" />
          </div>
          <CardTitle id="session-expired-title" className="text-2xl tracking-tight">
            Session expired
          </CardTitle>
          <CardDescription id="session-expired-desc">
            For your security, your sign-in session has ended. You will be taken
            to the login page in a moment.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-center text-sm text-muted-foreground">
            Redirecting in {secondsLeft}s…
          </p>
          <Button className="w-full" type="button" onClick={onContinue}>
            <LogIn className="h-4 w-4" />
            Sign in again
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
