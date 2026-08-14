import { useState } from 'react'
import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom'
import { FileText, Loader2, MessageSquare, Trash2 } from 'lucide-react'
import { DeleteCollectionDialog } from '@/components/collections/DeleteCollectionDialog'
import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import { useDeleteWorkspace, useWorkspace } from '@/hooks/useWorkspaces'
import { cn } from '@/lib/utils'

const tabs = [
  { to: 'documents', label: 'Documents', icon: FileText },
  { to: 'chat', label: 'Chat', icon: MessageSquare },
]

export default function WorkspaceLayout() {
  const { workspaceId } = useParams()
  const navigate = useNavigate()
  const { data: workspace } = useWorkspace(workspaceId)
  const deleteWorkspace = useDeleteWorkspace()
  const name = workspace?.name ?? 'Collection'
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  async function confirmDelete() {
    if (!workspaceId) return
    setDeleteError(null)
    try {
      await deleteWorkspace.mutateAsync(workspaceId)
      setConfirmOpen(false)
      navigate('/collections', { replace: true })
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : 'Failed to delete collection')
    }
  }

  return (
    <AppShell
      title={name}
      subtitle="Upload files, then ask questions with cited answers."
      actions={
        <div className="flex items-center gap-2">
          <nav className="flex gap-1 rounded-xl border border-border bg-card p-1 shadow-soft">
            {tabs.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={`/collections/${workspaceId}/${to}`}
                className={({ isActive }) =>
                  cn(
                    'inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition',
                    isActive
                      ? 'bg-primary text-primary-foreground shadow-soft'
                      : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )
                }
              >
                <Icon className="h-4 w-4" />
                {label}
              </NavLink>
            ))}
          </nav>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-muted-foreground hover:text-destructive"
            disabled={deleteWorkspace.isPending || !workspaceId}
            onClick={() => {
              setDeleteError(null)
              setConfirmOpen(true)
            }}
          >
            {deleteWorkspace.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Trash2 className="h-4 w-4" />
            )}
            Delete
          </Button>
        </div>
      }
    >
      <Outlet />
      <DeleteCollectionDialog
        open={confirmOpen}
        collectionName={name}
        pending={deleteWorkspace.isPending}
        error={deleteError}
        onConfirm={() => void confirmDelete()}
        onCancel={() => {
          if (!deleteWorkspace.isPending) {
            setConfirmOpen(false)
            setDeleteError(null)
          }
        }}
      />
    </AppShell>
  )
}
