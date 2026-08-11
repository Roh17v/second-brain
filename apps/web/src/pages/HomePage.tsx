import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useQueries } from '@tanstack/react-query'
import {
  ArrowRight,
  FileText,
  FolderKanban,
  Loader2,
  MessageSquare,
  Plus,
  Sparkles,
} from 'lucide-react'
import { apiRequest } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import type { Document, Workspace } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { AppShell } from '@/components/layout/AppShell'
import type { CommandItem } from '@/components/layout/CommandPalette'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { useWorkspaces } from '@/hooks/useWorkspaces'

type RecentDoc = Document & { collectionName: string; collectionId: string }

function greeting(name: string | null) {
  const hour = new Date().getHours()
  const part =
    hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
  const first = name?.split(/\s+/)[0]
  return first ? `${part}, ${first}` : part
}

export default function HomePage() {
  const { token, name } = useAuth()
  const {
    data: workspaces = [],
    isLoading: workspacesLoading,
    isError: workspacesError,
    error: workspacesErr,
  } = useWorkspaces()

  const slice = workspaces.slice(0, 8)
  const docQueries = useQueries({
    queries: slice.map((ws) => ({
      queryKey: queryKeys.documents(ws.id),
      queryFn: () =>
        apiRequest<Document[]>(
          `/api/workspaces/${ws.id}/documents`,
          {},
          token,
        ),
      enabled: Boolean(token && ws.id),
      staleTime: 30_000,
    })),
  })

  const docsLoading = docQueries.some((q) => q.isLoading)
  const loading = workspacesLoading || (slice.length > 0 && docsLoading)

  const { recentDocs, docCount } = useMemo(() => {
    const flat: RecentDoc[] = []
    docQueries.forEach((q, i) => {
      const ws = slice[i]
      if (!ws || !q.data) return
      for (const d of q.data) {
        flat.push({
          ...d,
          collectionName: ws.name,
          collectionId: ws.id,
        })
      }
    })
    flat.sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
    return { recentDocs: flat.slice(0, 6), docCount: flat.length }
  }, [docQueries, slice])

  const error = workspacesError
    ? workspacesErr instanceof Error
      ? workspacesErr.message
      : 'Failed to load home'
    : null

  const commandItems: CommandItem[] = useMemo(
    () =>
      workspaces.map((ws: Workspace) => ({
        id: `ws-${ws.id}`,
        label: ws.name,
        hint: 'Collection',
        group: 'Collections',
        icon: <FolderKanban className="h-4 w-4" />,
        to: `/collections/${ws.id}/documents`,
      })),
    [workspaces],
  )

  const stats = [
    {
      label: 'Documents',
      value: loading ? '—' : String(docCount),
      icon: FileText,
    },
    {
      label: 'Collections',
      value: loading ? '—' : String(workspaces.length),
      icon: FolderKanban,
    },
    {
      label: 'Chunks',
      value: '—',
      hint: 'Soon',
      icon: Sparkles,
    },
    {
      label: 'Indexed',
      value: '—',
      hint: 'Soon',
      icon: MessageSquare,
    },
  ]

  return (
    <AppShell commandItems={commandItems}>
      <div className="space-y-8">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            {greeting(name)}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Your knowledge workspace — not another empty chat.
          </p>
        </div>

        {error && (
          <div className="rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error}
          </div>
        )}

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {stats.map(({ label, value, icon: Icon, hint }) => (
            <Card key={label} className="shadow-soft">
              <CardContent className="flex items-center gap-3 p-4">
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-muted text-muted-foreground">
                  <Icon className="h-5 w-5" />
                </span>
                <div>
                  <p className="text-2xl font-semibold tracking-tight">{value}</p>
                  <p className="text-xs text-muted-foreground">
                    {label}
                    {hint ? ` · ${hint}` : ''}
                  </p>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
          <Card className="shadow-soft">
            <CardHeader className="flex flex-row items-start justify-between gap-3 space-y-0">
              <div>
                <CardTitle className="text-base">Recently added</CardTitle>
                <CardDescription>Latest documents across collections</CardDescription>
              </div>
              <Button asChild variant="outline" size="sm" className="shrink-0">
                <Link to="/collections">
                  All
                  <ArrowRight className="h-3.5 w-3.5" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading your brain…
                </div>
              ) : recentDocs.length === 0 ? (
                <div className="rounded-xl border border-dashed border-border px-4 py-10 text-center">
                  <p className="text-sm text-muted-foreground">
                    No documents yet. Create a collection and upload notes or PDFs.
                  </p>
                  <Button asChild className="mt-4" size="sm">
                    <Link to="/collections">
                      <Plus className="h-4 w-4" />
                      Get started
                    </Link>
                  </Button>
                </div>
              ) : (
                <ul className="divide-y divide-border">
                  {recentDocs.map((doc) => (
                    <li key={doc.id}>
                      <Link
                        to={`/collections/${doc.collectionId}/documents`}
                        className="flex items-center gap-3 py-3 transition hover:bg-muted/50 -mx-2 rounded-xl px-2"
                      >
                        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-muted">
                          <FileText className="h-4 w-4 text-muted-foreground" />
                        </span>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium">
                            {doc.originalFilename}
                          </p>
                          <p className="truncate text-xs text-muted-foreground">
                            {doc.collectionName}
                          </p>
                        </div>
                        <Badge variant="secondary" className="shrink-0">
                          {doc.status}
                        </Badge>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>

          <div className="space-y-6">
            <Card className="shadow-soft">
              <CardHeader>
                <CardTitle className="text-base">Ask anything</CardTitle>
                <CardDescription>
                  Open a collection chat to query your notes with citations.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {workspaces.slice(0, 4).map((ws) => (
                  <Button
                    key={ws.id}
                    asChild
                    variant="outline"
                    className="h-auto w-full justify-between py-3"
                  >
                    <Link to={`/collections/${ws.id}/chat`}>
                      <span className="flex items-center gap-2">
                        <MessageSquare className="h-4 w-4 text-muted-foreground" />
                        {ws.name}
                      </span>
                      <ArrowRight className="h-4 w-4 text-muted-foreground" />
                    </Link>
                  </Button>
                ))}
                {workspaces.length === 0 && !loading && (
                  <p className="text-sm text-muted-foreground">
                    Create a collection first, then start chatting.
                  </p>
                )}
              </CardContent>
            </Card>

            <Card className="border-dashed shadow-soft">
              <CardContent className="flex items-start gap-3 p-5">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-accent text-accent-foreground">
                  <Sparkles className="h-4 w-4" />
                </span>
                <div>
                  <p className="text-sm font-medium">Knowledge-first</p>
                  <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                    Press{' '}
                    <kbd className="rounded border border-border bg-muted px-1 py-0.5 text-[10px]">
                      Ctrl K
                    </kbd>{' '}
                    to search. Upload scans — OCR, chunk, embed, then chat with sources.
                  </p>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </AppShell>
  )
}
