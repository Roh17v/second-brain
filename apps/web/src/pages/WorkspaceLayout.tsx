import { useEffect, useState } from 'react'
import { NavLink, Outlet, useParams } from 'react-router-dom'
import { FileText, MessageSquare } from 'lucide-react'
import { apiRequest } from '@/api/client'
import type { Workspace } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { AppShell } from '@/components/layout/AppShell'
import { cn } from '@/lib/utils'

const tabs = [
  { to: 'documents', label: 'Documents', icon: FileText },
  { to: 'chat', label: 'Chat', icon: MessageSquare },
]

export default function WorkspaceLayout() {
  const { workspaceId } = useParams()
  const { token } = useAuth()
  const [name, setName] = useState<string>('Collection')

  useEffect(() => {
    if (!workspaceId) return
    void apiRequest<Workspace>(`/api/workspaces/${workspaceId}`, {}, token)
      .then((ws) => setName(ws.name))
      .catch(() => setName('Collection'))
  }, [workspaceId, token])

  return (
    <AppShell
      title={name}
      subtitle="Documents first — then chat with citations over this collection."
      actions={
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
      }
    >
      <Outlet />
    </AppShell>
  )
}
