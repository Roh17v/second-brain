import { AppShell } from '@/components/layout/AppShell'
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
  { id: 'light', label: 'Light', icon: Sun, hint: 'Light background' },
  { id: 'dark', label: 'Dark', icon: Moon, hint: 'Dark background' },
  { id: 'system', label: 'System', icon: Monitor, hint: 'Match device setting' },
]

export default function SettingsPage() {
  const { theme, setTheme } = useTheme()

  return (
    <AppShell
      title="Settings"
      subtitle="Appearance."
    >
      <div className="mx-auto max-w-2xl space-y-6">
        <Card className="shadow-soft">
          <CardHeader>
            <CardTitle className="text-base">Theme</CardTitle>
            <CardDescription>Choose how SecondBrain looks.</CardDescription>
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
      </div>
    </AppShell>
  )
}
