import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { Loader2, Maximize2, X, ZoomIn, ZoomOut } from 'lucide-react'
import { useTheme } from '@/theme/ThemeProvider'
import { cn } from '@/lib/utils'

type Props = {
  chart: string
  inverse?: boolean
  /** Incomplete stream — do not parse Mermaid yet */
  streaming?: boolean
}

const ZOOM_MIN = 0.5
const ZOOM_MAX = 3
const ZOOM_STEP = 0.25

function sanitizeId(raw: string): string {
  return raw.replace(/[^a-zA-Z0-9_-]/g, '_')
}

function ExpandedDiagram({
  svg,
  zoom,
  onZoom,
  onClose,
}: {
  svg: string
  zoom: number
  onZoom: (next: number) => void
  onClose: () => void
}) {
  const stageRef = useRef<HTMLDivElement>(null)
  const [fitWidth, setFitWidth] = useState(0)

  useLayoutEffect(() => {
    const stage = stageRef.current
    if (!stage) return
    const measure = () => {
      const w = stage.clientWidth
      if (w > 0) setFitWidth(w)
    }
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(stage)
    return () => ro.disconnect()
  }, [svg])

  const width = fitWidth > 0 ? Math.round(fitWidth * zoom) : undefined

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-3 sm:p-6"
      role="dialog"
      aria-modal="true"
      aria-label="Diagram"
      onClick={onClose}
    >
      <div
        className="flex h-[92vh] w-[96vw] flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-lift"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-2.5">
          <p className="text-sm font-medium">Diagram</p>
          <div className="flex items-center gap-1">
            <button
              type="button"
              className="rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-foreground"
              aria-label="Zoom out"
              onClick={() => onZoom(Math.max(ZOOM_MIN, +(zoom - ZOOM_STEP).toFixed(2)))}
            >
              <ZoomOut className="h-4 w-4" />
            </button>
            <span className="min-w-12 text-center text-xs tabular-nums text-muted-foreground">
              {Math.round(zoom * 100)}%
            </span>
            <button
              type="button"
              className="rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-foreground"
              aria-label="Zoom in"
              onClick={() => onZoom(Math.min(ZOOM_MAX, +(zoom + ZOOM_STEP).toFixed(2)))}
            >
              <ZoomIn className="h-4 w-4" />
            </button>
            <button
              type="button"
              className="ml-1 rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-foreground"
              aria-label="Close"
              onClick={onClose}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>
        <div ref={stageRef} className="min-h-0 flex-1 overflow-auto p-6">
          <div
            className="[&_svg]:!h-auto [&_svg]:!w-full [&_svg]:!max-w-none"
            style={width ? { width } : { width: '100%' }}
            dangerouslySetInnerHTML={{ __html: svg }}
          />
        </div>
      </div>
    </div>
  )
}

export function MermaidBlock({ chart, inverse, streaming }: Props) {
  const { resolved } = useTheme()
  const reactId = useId()
  const [svg, setSvg] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showSource, setShowSource] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [zoom, setZoom] = useState(1)

  const source = chart.trim()

  useEffect(() => {
    if (streaming || !source) {
      setSvg(null)
      setError(null)
      return
    }

    let cancelled = false
    const renderId = sanitizeId(`mermaid-${reactId}-${resolved}`)

    void (async () => {
      try {
        const mermaid = (await import('mermaid')).default
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict',
          theme: resolved === 'dark' ? 'dark' : 'default',
          fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif',
        })
        const { svg: next } = await mermaid.render(renderId, source)
        if (!cancelled) {
          setSvg(next)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) {
          setSvg(null)
          setError(err instanceof Error ? err.message : 'Could not render diagram')
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [source, resolved, reactId, streaming])

  useEffect(() => {
    if (!expanded) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setExpanded(false)
    }
    window.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [expanded])

  if (!source) return null

  const canExpand = Boolean(svg) && !error && !streaming

  return (
    <div
      className={cn(
        'my-3 overflow-hidden rounded-xl border',
        inverse ? 'border-white/20' : 'border-border',
      )}
    >
      <div
        className={cn(
          'flex items-center justify-between border-b px-3 py-1.5 text-[11px]',
          inverse ? 'border-white/20 text-primary-foreground/80' : 'border-border text-muted-foreground',
        )}
      >
        <span>Diagram</span>
        <div className="flex items-center gap-3">
          {canExpand && (
            <button
              type="button"
              className="inline-flex items-center gap-1 underline-offset-2 hover:underline"
              onClick={() => {
                setZoom(1)
                setExpanded(true)
              }}
            >
              <Maximize2 className="h-3 w-3" />
              Expand
            </button>
          )}
          <button
            type="button"
            className="underline-offset-2 hover:underline"
            onClick={() => setShowSource((v) => !v)}
          >
            {showSource ? 'Hide source' : 'Show source'}
          </button>
        </div>
      </div>

      {streaming || (!svg && !error) ? (
        <div className="flex items-center gap-2 px-3 py-6 text-xs text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Drawing…
        </div>
      ) : error || !svg ? (
        <pre
          className={cn(
            'overflow-x-auto px-3 py-2 font-mono text-xs',
            inverse ? 'bg-black/20' : 'bg-muted',
          )}
        >
          {error ? `Could not draw diagram.\n\n${source}` : source}
        </pre>
      ) : (
        <button
          type="button"
          className={cn(
            'block w-full cursor-zoom-in overflow-x-auto px-3 py-4 text-left [&_svg]:mx-auto [&_svg]:max-w-none',
            inverse ? 'bg-black/10' : 'bg-card',
          )}
          onClick={() => {
            setZoom(1)
            setExpanded(true)
          }}
          aria-label="Expand diagram"
        >
          <div dangerouslySetInnerHTML={{ __html: svg }} />
        </button>
      )}

      {showSource && !streaming && (
        <pre
          className={cn(
            'overflow-x-auto border-t px-3 py-2 font-mono text-xs',
            inverse ? 'border-white/20 bg-black/20' : 'border-border bg-muted',
          )}
        >
          {source}
        </pre>
      )}

      {expanded && svg && (
        <ExpandedDiagram
          svg={svg}
          zoom={zoom}
          onZoom={setZoom}
          onClose={() => setExpanded(false)}
        />
      )}
    </div>
  )
}
