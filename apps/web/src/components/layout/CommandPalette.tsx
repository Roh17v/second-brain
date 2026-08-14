import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  FileText,
  FolderKanban,
  Home,
  MessageSquare,
  Search,
  Settings,
  X,
} from 'lucide-react'
import { cn } from '@/lib/utils'

export type CommandItem = {
  id: string
  label: string
  hint?: string
  group: string
  icon?: React.ReactNode
  to?: string
  action?: () => void
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  extraItems?: CommandItem[]
}

const STATIC_ITEMS: CommandItem[] = [
  {
    id: 'home',
    label: 'Home',
    group: 'Navigation',
    icon: <Home className="h-4 w-4" />,
    to: '/',
  },
  {
    id: 'collections',
    label: 'Collections',
    group: 'Navigation',
    icon: <FolderKanban className="h-4 w-4" />,
    to: '/collections',
  },
  {
    id: 'settings',
    label: 'Settings',
    group: 'Navigation',
    icon: <Settings className="h-4 w-4" />,
    to: '/settings',
  },
]

export function CommandPalette({ open, onOpenChange, extraItems = [] }: Props) {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)

  const items = useMemo(() => {
    const all = [...STATIC_ITEMS, ...extraItems]
    const q = query.trim().toLowerCase()
    if (!q) return all
    return all.filter(
      (item) =>
        item.label.toLowerCase().includes(q) ||
        item.hint?.toLowerCase().includes(q) ||
        item.group.toLowerCase().includes(q),
    )
  }, [extraItems, query])

  useEffect(() => {
    if (!open) {
      setQuery('')
      setActive(0)
    }
  }, [open])

  useEffect(() => {
    setActive(0)
  }, [query])

  useEffect(() => {
    if (!open) return
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault()
        onOpenChange(false)
      } else if (e.key === 'ArrowDown') {
        e.preventDefault()
        setActive((i) => Math.min(i + 1, Math.max(items.length - 1, 0)))
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        setActive((i) => Math.max(i - 1, 0))
      } else if (e.key === 'Enter' && items[active]) {
        e.preventDefault()
        run(items[active])
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, items, active, onOpenChange])

  function run(item: CommandItem) {
    onOpenChange(false)
    if (item.action) item.action()
    if (item.to) navigate(item.to)
  }

  if (!open) return null

  const groups = [...new Set(items.map((i) => i.group))]

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 p-4 pt-[12vh] dark:bg-black/65">
      <button
        type="button"
        className="absolute inset-0 cursor-default"
        aria-label="Close search"
        onClick={() => onOpenChange(false)}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Search"
        className="relative z-10 w-full max-w-xl overflow-hidden rounded-2xl border border-border bg-card shadow-lift"
      >
        <div className="flex items-center gap-2 border-b border-border px-4">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search collections and pages…"
            className="h-12 w-full bg-transparent text-sm text-foreground outline-none ring-0 ring-offset-0 placeholder:text-muted-foreground focus:outline-none focus:ring-0 focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0"
          />
          <button
            type="button"
            onClick={() => onOpenChange(false)}
            className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="max-h-[50vh] overflow-auto p-2">
          {items.length === 0 ? (
            <p className="px-3 py-8 text-center text-sm text-muted-foreground">
              No matches.
            </p>
          ) : (
            groups.map((group) => {
              const groupItems = items.filter((i) => i.group === group)
              if (groupItems.length === 0) return null
              return (
                <div key={group} className="mb-2">
                  <p className="px-3 py-1.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                    {group}
                  </p>
                  <ul>
                    {groupItems.map((item) => {
                      const index = items.indexOf(item)
                      return (
                        <li key={item.id}>
                          <button
                            type="button"
                            onClick={() => run(item)}
                            onMouseEnter={() => setActive(index)}
                            className={cn(
                              'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition',
                              index === active
                                ? 'bg-accent text-accent-foreground'
                                : 'text-foreground hover:bg-muted',
                            )}
                          >
                            <span className="text-muted-foreground">
                              {item.icon ??
                                (item.group === 'Documents' ? (
                                  <FileText className="h-4 w-4" />
                                ) : (
                                  <MessageSquare className="h-4 w-4" />
                                ))}
                            </span>
                            <span className="flex-1 truncate font-medium">{item.label}</span>
                            {item.hint && (
                              <span className="truncate text-xs text-muted-foreground">
                                {item.hint}
                              </span>
                            )}
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              )
            })
          )}
        </div>

        <div className="flex items-center justify-between border-t border-border px-4 py-2 text-[11px] text-muted-foreground">
          <span>↑↓ navigate · ↵ open · esc close</span>
          <span className="rounded-md border border-border px-1.5 py-0.5 font-medium">
            Ctrl K
          </span>
        </div>
      </div>
    </div>
  )
}
