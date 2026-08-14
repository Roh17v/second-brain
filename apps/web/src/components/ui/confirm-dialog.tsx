import { useEffect, type ReactNode } from 'react'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { cn } from '@/lib/utils'

type ConfirmDialogProps = {
  open: boolean
  title: string
  description?: ReactNode
  icon?: ReactNode
  confirmLabel?: string
  cancelLabel?: string
  variant?: 'default' | 'destructive'
  pending?: boolean
  error?: string | null
  onConfirm: () => void
  onCancel: () => void
}

/**
 * App-styled modal confirm (replaces window.confirm).
 * Escape / backdrop click cancel unless a request is in flight.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  icon,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'default',
  pending = false,
  error,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !pending) onCancel()
    }
    window.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [open, pending, onCancel])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-[90] flex items-center justify-center bg-background/80 p-4 backdrop-blur-sm"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !pending) onCancel()
      }}
    >
      <Card
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-desc"
        className="w-full max-w-md shadow-lift"
      >
        <CardHeader className="space-y-3">
          {icon && (
            <div
              className={cn(
                'flex h-11 w-11 items-center justify-center rounded-2xl',
                variant === 'destructive'
                  ? 'bg-destructive/10 text-destructive'
                  : 'bg-primary/10 text-primary',
              )}
            >
              {icon}
            </div>
          )}
          <CardTitle id="confirm-dialog-title" className="text-xl tracking-tight">
            {title}
          </CardTitle>
          {description && (
            <div
              id="confirm-dialog-desc"
              className="text-sm leading-relaxed text-muted-foreground"
            >
              {description}
            </div>
          )}
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <div className="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </div>
          )}
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="ghost"
              disabled={pending}
              onClick={onCancel}
            >
              {cancelLabel}
            </Button>
            <Button
              type="button"
              variant={variant === 'destructive' ? 'destructive' : 'default'}
              disabled={pending}
              onClick={onConfirm}
            >
              {pending && <Loader2 className="h-4 w-4 animate-spin" />}
              {confirmLabel}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
