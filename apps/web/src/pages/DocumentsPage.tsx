import { FormEvent, useState } from 'react'
import { useParams } from 'react-router-dom'
import { CheckCircle2, FileUp, Loader2, RotateCcw, XCircle } from 'lucide-react'
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
import {
  useDocuments,
  useRetryDocument,
  useUploadDocument,
} from '@/hooks/useDocuments'
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
      return 'Ready'
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
  const {
    data: documents = [],
    isLoading,
    isError,
    error: loadError,
  } = useDocuments(workspaceId)
  const upload = useUploadDocument(workspaceId)
  const retry = useRetryDocument(workspaceId)

  const [file, setFile] = useState<File | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  async function onUpload(e: FormEvent) {
    e.preventDefault()
    if (!file || !token) return
    setActionError(null)
    try {
      await upload.mutateAsync(file)
      setFile(null)
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Upload failed')
    }
  }

  async function retryDoc(id: string) {
    setActionError(null)
    try {
      await retry.mutateAsync(id)
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Retry failed')
    }
  }

  const readyCount = documents.filter((d: Document) => d.status === 'READY').length
  const pendingCount = documents.filter((d: Document) => isInFlight(d.status)).length
  const error =
    actionError ||
    (isError
      ? loadError instanceof Error
        ? loadError.message
        : 'Failed to load documents'
      : null)

  return (
    <div className="grid gap-6 lg:grid-cols-[340px_1fr]">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <FileUp className="h-4 w-4 text-primary" />
            Upload document
          </CardTitle>
          <CardDescription>PDF, images, TXT, or Markdown. Max 20 MB.</CardDescription>
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
            <Button
              type="submit"
              className="w-full"
              disabled={!file || upload.isPending}
            >
              {upload.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Upload
            </Button>
            <p className="text-xs leading-relaxed text-muted-foreground">
              Files are processed in the background. Ready documents can be used in chat.
            </p>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Files in this collection</CardTitle>
          <CardDescription>
            {readyCount} ready
            {pendingCount > 0 ? ` · ${pendingCount} processing` : ''}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
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
                      disabled={retry.isPending || upload.isPending}
                      onClick={() => void retryDoc(doc.id)}
                    >
                      {retry.isPending && retry.variables === doc.id ? (
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
    { key: 'PROCESSING', label: 'Processing' },
    { key: 'EMBEDDING', label: 'Indexing' },
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
