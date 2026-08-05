import { AppShell } from '@/components/layout/AppShell'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { useTheme, type ThemeMode } from '@/theme/ThemeProvider'
import { Monitor, Moon, Sun } from 'lucide-react'

const themes: { id: ThemeMode; label: string; icon: typeof Sun; hint: string }[] = [
  { id: 'light', label: 'Light', icon: Sun, hint: 'Clean paper-like UI' },
  { id: 'dark', label: 'Dark', icon: Moon, hint: 'Low glare for long sessions' },
  { id: 'system', label: 'System', icon: Monitor, hint: 'Follow OS preference' },
]

export default function SettingsPage() {
  const { theme, setTheme } = useTheme()

  return (
    <AppShell
      title="Settings"
      subtitle="Appearance and future model preferences. Providers wire up next."
    >
      <div className="mx-auto max-w-2xl space-y-6">
        <Card className="shadow-soft">
          <CardHeader>
            <CardTitle className="text-base">Theme</CardTitle>
            <CardDescription>Notion-like light, calm dark, or system.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-3">
            {themes.map(({ id, label, icon: Icon, hint }) => (
              <button
                key={id}
                type="button"
                onClick={() => setTheme(id)}
                className={cn(
                  'flex flex-col items-start gap-2 rounded-xl border p-4 text-left transition',
                  theme === id
                    ? 'border-primary bg-accent/60 shadow-soft'
                    : 'border-border bg-card hover:bg-muted/60',
                )}
              >
                <Icon className="h-5 w-5 text-muted-foreground" />
                <span className="text-sm font-medium">{label}</span>
                <span className="text-xs text-muted-foreground">{hint}</span>
              </button>
            ))}
          </CardContent>
        </Card>

        <Card className="shadow-soft opacity-90">
          <CardHeader>
            <CardTitle className="text-base">Models (coming soon)</CardTitle>
            <CardDescription>
              LLM, embeddings, and OCR providers will be selectable here. Today they are
              configured on the server (Ollama + Mistral OCR).
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-2 text-sm text-muted-foreground">
            <p>LLM · Embeddings · OCR — UI placeholders only.</p>
            <Button variant="outline" size="sm" disabled>
              Configure providers
            </Button>
          </CardContent>
        </Card>
      </div>
    </AppShell>
  )
}
