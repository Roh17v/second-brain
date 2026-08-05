import ReactMarkdown from 'react-markdown'
import { cn } from '@/lib/utils'

/**
 * Renders LLM markdown (bold, lists, code, etc.) so raw ** markers don't show.
 */
export function MarkdownMessage({
  content,
  className,
  inverse,
}: {
  content: string
  className?: string
  /** User bubble: lighter contrast on primary background */
  inverse?: boolean
}) {
  if (!content) return null

  return (
    <div
      className={cn(
        'markdown-body text-sm leading-relaxed break-words',
        inverse ? 'markdown-inverse' : '',
        className,
      )}
    >
      <ReactMarkdown
        components={{
          p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
          strong: ({ children }) => (
            <strong className="font-semibold">{children}</strong>
          ),
          em: ({ children }) => <em className="italic">{children}</em>,
          ul: ({ children }) => (
            <ul className="mb-2 list-disc space-y-1 pl-5 last:mb-0">{children}</ul>
          ),
          ol: ({ children }) => (
            <ol className="mb-2 list-decimal space-y-1 pl-5 last:mb-0">{children}</ol>
          ),
          li: ({ children }) => <li className="leading-relaxed">{children}</li>,
          h1: ({ children }) => (
            <h3 className="mb-2 mt-3 text-base font-semibold first:mt-0">{children}</h3>
          ),
          h2: ({ children }) => (
            <h3 className="mb-2 mt-3 text-base font-semibold first:mt-0">{children}</h3>
          ),
          h3: ({ children }) => (
            <h4 className="mb-1.5 mt-2 text-sm font-semibold first:mt-0">{children}</h4>
          ),
          a: ({ href, children }) => (
            <a
              href={href}
              target="_blank"
              rel="noreferrer"
              className={cn(
                'underline underline-offset-2',
                inverse ? 'text-primary-foreground' : 'text-primary',
              )}
            >
              {children}
            </a>
          ),
          code: ({ className: codeClass, children }) => {
            const isBlock = Boolean(codeClass)
            if (isBlock) {
              return (
                <code
                  className={cn(
                    'my-2 block overflow-x-auto rounded-lg px-3 py-2 font-mono text-xs',
                    inverse ? 'bg-black/20' : 'bg-muted',
                  )}
                >
                  {children}
                </code>
              )
            }
            return (
              <code
                className={cn(
                  'rounded px-1 py-0.5 font-mono text-[0.85em]',
                  inverse ? 'bg-black/20' : 'bg-muted',
                )}
              >
                {children}
              </code>
            )
          },
          pre: ({ children }) => (
            <pre className="my-2 overflow-x-auto rounded-lg last:mb-0">{children}</pre>
          ),
          blockquote: ({ children }) => (
            <blockquote
              className={cn(
                'my-2 border-l-2 pl-3 italic opacity-90',
                inverse ? 'border-white/40' : 'border-border',
              )}
            >
              {children}
            </blockquote>
          ),
          hr: () => (
            <hr
              className={cn(
                'my-3 border-0 border-t',
                inverse ? 'border-white/20' : 'border-border',
              )}
            />
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
