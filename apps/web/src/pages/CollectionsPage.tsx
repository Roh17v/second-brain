import { FormEvent, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, FolderKanban, FolderPlus, Loader2, Plus } from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { AppShell } from '@/components/layout/AppShell'
import type { CommandItem } from '@/components/layout/CommandPalette'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useCreateWorkspace, useWorkspaces } from '@/hooks/useWorkspaces'

/** Product language: Collections. API entity remains Workspace. */
export default function CollectionsPage() {
  const { token } = useAuth()
  const { data: workspaces = [], isLoading, isError, error: loadError } =
    useWorkspaces()
  const createWorkspace = useCreateWorkspace()

  const [newName, setNewName] = useState('')
  const [description, setDescription] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  async function onCreate(e: FormEvent) {
    e.preventDefault()
    if (!token) return
    setFormError(null)
    try {
      await createWorkspace.mutateAsync({
        name: newName,
        description: description || null,
      })
      setNewName('')
      setDescription('')
      setShowForm(false)
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to create collection')
    }
  }

  const commandItems: CommandItem[] = useMemo(
    () =>
      workspaces.map((ws) => ({
        id: `col-${ws.id}`,
        label: ws.name,
        group: 'Collections',
        to: `/collections/${ws.id}/documents`,
      })),
    [workspaces],
  )

  const listError =
    isError && loadError instanceof Error
      ? loadError.message
      : isError
        ? 'Failed to load collections'
        : null
  const error = formError || listError

  return (
    <AppShell
      title="Collections"
      subtitle="Card-based knowledge spaces — books, coding, college, work."
      commandItems={commandItems}
      actions={
        <Button size="sm" onClick={() => setShowForm((v) => !v)}>
          <Plus className="h-4 w-4" />
          New collection
        </Button>
      }
    >
      <div className="space-y-6">
        {showForm && (
          <Card className="max-w-lg shadow-soft">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <FolderPlus className="h-4 w-4 text-primary" />
                Create collection
              </CardTitle>
              <CardDescription>
                Each collection has its own documents and chats.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form className="space-y-4" onSubmit={onCreate}>
                {formError && (
                  <div className="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                    {formError}
                  </div>
                )}
                <div className="space-y-2">
                  <Label htmlFor="col-name">Name</Label>
                  <Input
                    id="col-name"
                    placeholder="e.g. System Design"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="col-desc">Description</Label>
                  <Input
                    id="col-desc"
                    placeholder="Optional"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </div>
                <div className="flex gap-2">
                  <Button type="submit" disabled={createWorkspace.isPending}>
                    {createWorkspace.isPending && (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    )}
                    Create
                  </Button>
                  <Button type="button" variant="ghost" onClick={() => setShowForm(false)}>
                    Cancel
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        )}

        {error && !showForm && (
          <div className="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </div>
        )}

        {isLoading ? (
          <Card className="p-10 text-center text-sm text-muted-foreground shadow-soft">
            <Loader2 className="mx-auto mb-2 h-5 w-5 animate-spin" />
            Loading collections…
          </Card>
        ) : workspaces.length === 0 ? (
          <Card className="border-dashed p-10 text-center shadow-soft">
            <p className="text-sm text-muted-foreground">
              No collections yet. Create one to upload notes and start chatting.
            </p>
            <Button className="mt-4" size="sm" onClick={() => setShowForm(true)}>
              <Plus className="h-4 w-4" />
              New collection
            </Button>
          </Card>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {workspaces.map((ws) => (
              <Link key={ws.id} to={`/collections/${ws.id}/documents`} className="group">
                <Card className="h-full shadow-soft transition group-hover:-translate-y-0.5 group-hover:shadow-lift">
                  <CardHeader>
                    <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-xl bg-muted text-muted-foreground">
                      <FolderKanban className="h-5 w-5" />
                    </div>
                    <CardTitle className="text-base">{ws.name}</CardTitle>
                    <CardDescription className="line-clamp-2 min-h-10">
                      {ws.description || 'No description'}
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="flex items-center justify-between text-sm text-muted-foreground">
                    <span>Open</span>
                    <ArrowRight className="h-4 w-4 transition group-hover:translate-x-0.5" />
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </div>
    </AppShell>
  )
}
