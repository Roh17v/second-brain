import { useEffect, useMemo, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import {
  Brain,
  FileText,
  FolderKanban,
  Home,
  LogOut,
  MessageSquare,
  Moon,
  Search,
  Settings,
  Sun,
  Monitor,
} from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useTheme } from '@/theme/ThemeProvider'
import { CommandPalette, type CommandItem } from './CommandPalette'

const mainNav = [
  { to: '/', label: 'Home', icon: Home, end: true },
  { to: '/collections', label: 'Collections', icon: FolderKanban },
  { to: '/settings', label: 'Settings', icon: Settings },
]

export function AppShell({
  children,
  title,
  subtitle,
  actions,
  commandItems,
}: {
  children: React.ReactNode
  title?: string
  subtitle?: string
  actions?: React.ReactNode
  /** Extra Ctrl+K entries (collections, docs, chats) */
  commandItems?: CommandItem[]
}) {
  const { name, email, logout } = useAuth()
  const { theme, cycleTheme } = useTheme()
  const location = useLocation()
  const navigate = useNavigate()
  const [paletteOpen, setPaletteOpen] = useState(false)

  // Workspace-scoped secondary nav when inside a collection
  const collectionMatch = location.pathname.match(/^\/collections\/([^/]+)/)
  const collectionId = collectionMatch?.[1]

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((v) => !v)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const ThemeIcon = theme === 'dark' ? Moon : theme === 'light' ? Sun : Monitor

  const initials = useMemo(() => {
    const source = name || email || '?'
    return source
      .split(/\s+/)
      .map((p) => p[0])
      .join('')
      .slice(0, 2)
      .toUpperCase()
  }, [name, email])

  return (
    <div className="flex h-full min-h-0 bg-background">
      {/* Sidebar */}
      <aside className="hidden w-[240px] shrink-0 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground md:flex">
        <div className="flex h-14 items-center gap-2 px-4">
          <Link to="/" className="flex items-center gap-2.5 font-semibold text-foreground">
            <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-soft">
              <Brain className="h-4 w-4" />
            </span>
            <span className="tracking-tight">SecondBrain</span>
          </Link>
        </div>

        <nav className="flex flex-1 flex-col gap-1 px-3 py-2">
          {mainNav.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium transition',
                  isActive
                    ? 'bg-sidebar-accent text-foreground shadow-soft'
                    : 'text-muted-foreground hover:bg-sidebar-accent/70 hover:text-foreground',
                )
              }
            >
              <Icon className="h-4 w-4 shrink-0" />
              {label}
            </NavLink>
          ))}

          {collectionId && (
            <>
              <div className="my-3 px-3">
                <div className="h-px bg-sidebar-border" />
                <p className="mt-3 mb-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                  This collection
                </p>
              </div>
              <NavLink
                to={`/collections/${collectionId}/documents`}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium transition',
                    isActive
                      ? 'bg-sidebar-accent text-foreground shadow-soft'
                      : 'text-muted-foreground hover:bg-sidebar-accent/70 hover:text-foreground',
                  )
                }
              >
                <FileText className="h-4 w-4" />
                Documents
              </NavLink>
              <NavLink
                to={`/collections/${collectionId}/chat`}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium transition',
                    isActive
                      ? 'bg-sidebar-accent text-foreground shadow-soft'
                      : 'text-muted-foreground hover:bg-sidebar-accent/70 hover:text-foreground',
                  )
                }
              >
                <MessageSquare className="h-4 w-4" />
                Chat
              </NavLink>
            </>
          )}
        </nav>

        <div className="border-t border-sidebar-border p-3">
          <div className="flex items-center gap-2 rounded-xl px-2 py-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-muted text-xs font-semibold">
              {initials}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{name || 'User'}</p>
              <p className="truncate text-xs text-muted-foreground">{email}</p>
            </div>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 shrink-0"
              title="Log out"
              onClick={() => {
                logout()
                navigate('/login')
              }}
            >
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </aside>

      {/* Main column */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-border bg-background/80 px-4 backdrop-blur-md sm:px-6">
          {/* Mobile logo */}
          <Link to="/" className="flex items-center gap-2 font-semibold md:hidden">
            <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <Brain className="h-4 w-4" />
            </span>
          </Link>

          <button
            type="button"
            onClick={() => setPaletteOpen(true)}
            className="flex h-9 max-w-md flex-1 items-center gap-2 rounded-xl border border-border bg-card px-3 text-left text-sm text-muted-foreground shadow-soft transition hover:border-primary/30 hover:text-foreground"
          >
            <Search className="h-4 w-4 shrink-0" />
            <span className="flex-1 truncate">Search your brain…</span>
            <kbd className="hidden rounded-md border border-border bg-muted px-1.5 py-0.5 text-[10px] font-medium sm:inline">
              Ctrl K
            </kbd>
          </button>

          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              size="icon"
              className="h-9 w-9"
              title={`Theme: ${theme}`}
              onClick={cycleTheme}
            >
              <ThemeIcon className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-9 w-9 md:hidden"
              title="Settings"
              onClick={() => navigate('/settings')}
            >
              <Settings className="h-4 w-4" />
            </Button>
          </div>
        </header>

        {/* Mobile bottom nav */}
        <nav className="fixed inset-x-0 bottom-0 z-20 flex border-t border-border bg-card/95 backdrop-blur md:hidden">
          {mainNav.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'flex flex-1 flex-col items-center gap-0.5 py-2 text-[10px] font-medium',
                  isActive ? 'text-primary' : 'text-muted-foreground',
                )
              }
            >
              <Icon className="h-5 w-5" />
              {label}
            </NavLink>
          ))}
        </nav>

        <main className="min-h-0 flex-1 overflow-auto px-4 py-6 pb-20 sm:px-6 md:pb-8">
          <div className="mx-auto w-full max-w-6xl">
            {(title || actions) && (
              <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  {title && (
                    <h1 className="text-2xl font-semibold tracking-tight text-foreground">
                      {title}
                    </h1>
                  )}
                  {subtitle && (
                    <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
                  )}
                </div>
                {actions}
              </div>
            )}
            {children}
          </div>
        </main>
      </div>

      <CommandPalette
        open={paletteOpen}
        onOpenChange={setPaletteOpen}
        extraItems={commandItems}
      />
    </div>
  )
}
