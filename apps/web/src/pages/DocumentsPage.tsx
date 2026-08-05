import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { CheckCircle2, FileUp, Loader2, RotateCcw, XCircle } from 'lucide-react'
import { apiRequest } from '@/api/client'
import type { Document } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

function statusVariant(status: string) {
  if (status === 'READY') return 'success' as const
  if (status === 'FAILED') return 'warning' as const
  return 'secondary' as const
}

function statusLabel(status: string) {
  switch (status) {
    case 'UPLOADED':
      return 'Queued'
    case 'PROCESSING':
      return 'Processing…'
    case 'EMBEDDING':
      return 'Embedding…'
    case 'READY':
      return 'Ready for chat'
    case 'FAILED':
      return 'Failed'
    default:
      return status
  }
}

function isInFlight(status: string) {
  return status === 'UPLOADED' || status === 'PROCESSING' || status === 'EMBEDDING'
}

export default function DocumentsPage() {
  const { workspaceId } = useParams()
  const { token } = useAuth()
  const [documents, setDocuments] = useState<Document[]>([])
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async (silent = false) => {
    if (!workspaceId) return
    if (!silent) setLoading(true)
    try {
      const data = await apiRequest<Document[]>(
        `/api/workspaces/${workspaceId}/documents`,
        {},
        token,
      )
      setDocuments(data)
      if (!silent) setError(null)
    } catch (err) {
      if (!silent) {
        setError(err instanceof Error ? err.message : 'Failed to load documents')
      }
    } finally {
      if (!silent) setLoading(false)
    }
  }, [workspaceId, token])

  useEffect(() => {
    void load()
  }, [load])

  // Poll while any document is still ingesting
  const needsPoll = useMemo(
    () => documents.some((d) => isInFlight(d.status)),
    [documents],
  )

  useEffect(() => {
    if (!needsPoll) return
    const id = window.setInterval(() => {
      void load(true)
    }, 2500)
    return () => window.clearInterval(id)
  }, [needsPoll, load])

  async function onUpload(e: FormEvent) {
    e.preventDefault()
    if (!workspaceId || !file) return
    setError(null)
    setBusyId('upload')
    try {
      const body = new FormData()
      body.append('file', file)
      await apiRequest<Document>(
        `/api/workspaces/${workspaceId}/documents`,
        { method: 'POST', body },
        token,
      )
      setFile(null)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setBusyId(null)
    }
  }

  async function retryDoc(id: string) {
    if (!workspaceId) return
    setBusyId(`${id}:retry`)
    setError(null)
    try {
      await apiRequest(
        `/api/workspaces/${workspaceId}/documents/${id}/retry`,
        { method: 'POST' },
        token,
      )
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Retry failed')
    } finally {
      setBusyId(null)
    }
  }

  const readyCount = documents.filter((d) => d.status === 'READY').length
  const pendingCount = documents.filter((d) => isInFlight(d.status)).length

  return (
    <div className="grid gap-6 lg:grid-cols-[340px_1fr]">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <FileUp className="h-4 w-4 text-primary" />
            Upload document
          </CardTitle>
          <CardDescription>PDF, images, TXT, or Markdown · max 20MB</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onUpload}>
            {error && (
              <div className="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {error}
              </div>
            )}
            <Input
              type="file"
              accept=".pdf,.txt,.md,.markdown,.png,.jpg,.jpeg,.webp,.gif,.avif"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            <Button type="submit" className="w-full" disabled={!file || busyId === 'upload'}>
              {busyId === 'upload' && <Loader2 className="h-4 w-4 animate-spin" />}
              Upload
            </Button>
            <p className="text-xs leading-relaxed text-muted-foreground">
              After upload we automatically extract text (OCR if needed), chunk, and embed
              in the background. You can keep chatting with docs already marked Ready.
            </p>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Files in this collection</CardTitle>
          <CardDescription>
            {readyCount} ready for chat
            {pendingCount > 0 ? ` · ${pendingCount} processing` : ''}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <p className="text-sm text-muted-foreground">Loading…</p>
          ) : documents.length === 0 ? (
            <p className="text-sm text-muted-foreground">No documents yet.</p>
          ) : (
            <ul className="divide-y divide-border">
              {documents.map((doc) => (
                <li
                  key={doc.id}
                  className="flex flex-col gap-3 py-4 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0 space-y-1">
                    <div className="truncate font-medium">{doc.originalFilename}</div>
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <Badge variant={statusVariant(doc.status)} className="gap-1">
                        {isInFlight(doc.status) && (
                          <Loader2 className="h-3 w-3 animate-spin" />
                        )}
                        {doc.status === 'READY' && <CheckCircle2 className="h-3 w-3" />}
                        {doc.status === 'FAILED' && <XCircle className="h-3 w-3" />}
                        {statusLabel(doc.status)}
                      </Badge>
                      <span>{(doc.sizeBytes / 1024).toFixed(1)} KB</span>
                    </div>
                    {doc.status === 'FAILED' && doc.failureReason && (
                      <p className="text-xs text-destructive line-clamp-2">
                        {doc.failureReason}
                      </p>
                    )}
                    {isInFlight(doc.status) && (
                      <PipelineSteps status={doc.status} />
                    )}
                  </div>
                  {doc.status === 'FAILED' && (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={busyId !== null}
                      onClick={() => void retryDoc(doc.id)}
                    >
                      {busyId === `${doc.id}:retry` ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <RotateCcw className="h-4 w-4" />
                      )}
                      Retry
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function PipelineSteps({ status }: { status: string }) {
  const steps = [
    { key: 'UPLOADED', label: 'Queued' },
    { key: 'PROCESSING', label: 'Extract & chunk' },
    { key: 'EMBEDDING', label: 'Embed' },
  ]
  const order = ['UPLOADED', 'PROCESSING', 'EMBEDDING', 'READY']
  const current = order.indexOf(status)

  return (
    <div className="mt-1 flex flex-wrap gap-1.5">
      {steps.map((step, i) => {
        const active = order.indexOf(step.key) <= current
        const isCurrent = step.key === status
        return (
          <span
            key={step.key}
            className={cn(
              'rounded-md px-1.5 py-0.5 text-[10px] font-medium',
              isCurrent && 'bg-primary/15 text-primary',
              active && !isCurrent && 'bg-muted text-muted-foreground',
              !active && 'text-muted-foreground/50',
            )}
          >
            {i + 1}. {step.label}
          </span>
        )
      })}
    </div>
  )
}
