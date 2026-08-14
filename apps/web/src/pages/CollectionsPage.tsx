import { FormEvent, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, FolderKanban, FolderPlus, Loader2, Plus, Trash2 } from 'lucide-react'
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
import { DeleteCollectionDialog } from '@/components/collections/DeleteCollectionDialog'
import { useCreateWorkspace, useDeleteWorkspace, useWorkspaces } from '@/hooks/useWorkspaces'

/** Product language: Collections. API entity remains Workspace. */
export default function CollectionsPage() {
  const { token } = useAuth()
  const { data: workspaces = [], isLoading, isError, error: loadError } =
    useWorkspaces()
  const createWorkspace = useCreateWorkspace()
  const deleteWorkspace = useDeleteWorkspace()

  const [newName, setNewName] = useState('')
  const [description, setDescription] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<{
    id: string
    name: string
  } | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

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

  function askDelete(workspaceId: string, name: string) {
    setFormError(null)
    setDeleteError(null)
    setPendingDelete({ id: workspaceId, name })
  }

  async function confirmDelete() {
    if (!pendingDelete) return
    setDeleteError(null)
    try {
      await deleteWorkspace.mutateAsync(pendingDelete.id)
      setPendingDelete(null)
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : 'Failed to delete collection')
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
      subtitle="Organize documents and chats by topic."
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
                Documents and chats stay inside this collection.
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
              No collections yet. Create one to upload files and chat.
            </p>
            <Button className="mt-4" size="sm" onClick={() => setShowForm(true)}>
              <Plus className="h-4 w-4" />
              New collection
            </Button>
          </Card>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {workspaces.map((ws) => (
              <Card
                key={ws.id}
                className="relative h-full shadow-soft transition hover:-translate-y-0.5 hover:shadow-lift"
              >
                <Link to={`/collections/${ws.id}/documents`} className="group block">
                  <CardHeader>
                    <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-xl bg-muted text-muted-foreground">
                      <FolderKanban className="h-5 w-5" />
                    </div>
                    <CardTitle className="text-base pr-10">{ws.name}</CardTitle>
                    <CardDescription className="line-clamp-2 min-h-10">
                      {ws.description || 'No description'}
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="flex items-center justify-between text-sm text-muted-foreground">
                    <span>Open</span>
                    <ArrowRight className="h-4 w-4 transition group-hover:translate-x-0.5" />
                  </CardContent>
                </Link>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="absolute right-2 top-2 h-8 w-8 text-muted-foreground hover:text-destructive"
                  aria-label={`Delete ${ws.name}`}
                  disabled={deleteWorkspace.isPending}
                  onClick={() => askDelete(ws.id, ws.name)}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </Card>
            ))}
          </div>
        )}
      </div>

      <DeleteCollectionDialog
        open={pendingDelete !== null}
        collectionName={pendingDelete?.name ?? 'this collection'}
        pending={deleteWorkspace.isPending}
        error={deleteError}
        onConfirm={() => void confirmDelete()}
        onCancel={() => {
          if (!deleteWorkspace.isPending) {
            setPendingDelete(null)
            setDeleteError(null)
          }
        }}
      />
    </AppShell>
  )
}
