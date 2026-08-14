import { FormEvent, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Loader2, MessageSquarePlus, Send } from 'lucide-react'
import { apiRequest, handleUnauthorized } from '@/api/client'
import type {
  ChatAnswer,
  Conversation,
  ConversationDetail,
  ChatMessage,
  Citation,
} from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { MarkdownMessage } from '@/components/chat/MarkdownMessage'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'

const API_BASE = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

function parseSseChunk(raw: string): { eventName: string; data: string } | null {
  const lines = raw.split('\n')
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (dataLines.length === 0) return null
  return { eventName, data: dataLines.join('\n') }
}

export default function ChatPage() {
  const { workspaceId } = useParams()
  const { token } = useAuth()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [model, setModel] = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  async function loadConversations() {
    if (!workspaceId) return
    const data = await apiRequest<Conversation[]>(
      `/api/workspaces/${workspaceId}/conversations`,
      {},
      token,
    )
    setConversations(data)
    if (!activeId && data.length > 0) setActiveId(data[0].id)
  }

  async function loadConversation(id: string) {
    if (!workspaceId) return
    const detail = await apiRequest<ConversationDetail>(
      `/api/workspaces/${workspaceId}/conversations/${id}`,
      {},
      token,
    )
    setMessages(detail.messages)
  }

  useEffect(() => {
    void loadConversations().catch((err) =>
      setError(err instanceof Error ? err.message : 'Failed to load conversations'),
    )
  }, [workspaceId, token])

  useEffect(() => {
    if (!activeId) {
      setMessages([])
      return
    }
    void loadConversation(activeId).catch((err) =>
      setError(err instanceof Error ? err.message : 'Failed to load messages'),
    )
  }, [activeId, workspaceId, token])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  async function createConversation() {
    if (!workspaceId) return
    setError(null)
    try {
      const created = await apiRequest<Conversation>(
        `/api/workspaces/${workspaceId}/conversations`,
        {
          method: 'POST',
          body: JSON.stringify({ title: 'New conversation' }),
        },
        token,
      )
      setConversations((prev) => [created, ...prev])
      setActiveId(created.id)
      setMessages([])
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create conversation')
    }
  }

  async function onSend(e: FormEvent) {
    e.preventDefault()
    if (!workspaceId || !activeId || !input.trim() || !token) return

    const text = input.trim()
    setSending(true)
    setError(null)
    setInput('')

    const tempUserId = `temp-user-${Date.now()}`
    const tempAssistantId = `temp-assistant-${Date.now()}`

    setMessages((prev) => [
      ...prev,
      {
        id: tempUserId,
        role: 'USER',
        content: text,
        createdAt: new Date().toISOString(),
        citations: [],
      },
      {
        id: tempAssistantId,
        role: 'ASSISTANT',
        content: '',
        createdAt: new Date().toISOString(),
        citations: [],
      },
    ])

    let gotDone = false
    let streamError: string | null = null

    try {
      const response = await fetch(
        `${API_BASE}/api/workspaces/${workspaceId}/conversations/${activeId}/messages/stream`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'text/event-stream',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({ message: text, topK: 5 }),
        },
      )

      if (!response.ok || !response.body) {
        if (response.status === 401) {
          handleUnauthorized()
          throw new Error('Session expired')
        }
        const errText = await response.text()
        let message = response.statusText
        try {
          message = JSON.parse(errText).message || message
        } catch {
          /* ignore */
        }
        throw new Error(message || 'Stream failed')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        let readResult: ReadableStreamReadResult<Uint8Array>
        try {
          readResult = await reader.read()
        } catch (readErr) {
          if (gotDone) break
          throw readErr
        }

        const { done, value } = readResult
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const parts = buffer.split('\n\n')
        buffer = parts.pop() ?? ''

        for (const part of parts) {
          const parsed = parseSseChunk(part)
          if (!parsed) continue

          let data: Record<string, unknown>
          try {
            data = JSON.parse(parsed.data) as Record<string, unknown>
          } catch {
            continue
          }

          if (parsed.eventName === 'user') {
            const userMessage = data.userMessage as ChatMessage
            const citations = (data.citations as Citation[]) || []
            if (data.model) setModel(String(data.model))
            setMessages((prev) =>
              prev.map((m) => {
                if (m.id === tempUserId) return { ...userMessage, citations: [] }
                if (m.id === tempAssistantId) return { ...m, citations }
                return m
              }),
            )
          }

          if (parsed.eventName === 'token') {
            const delta = String(data.delta ?? '')
            setMessages((prev) =>
              prev.map((m) =>
                m.id === tempAssistantId ? { ...m, content: m.content + delta } : m,
              ),
            )
          }

          if (parsed.eventName === 'done') {
            gotDone = true
            const answer = data as unknown as ChatAnswer
            setModel(answer.model)
            setMessages((prev) => {
              const cleaned = prev.filter(
                (m) =>
                  m.id !== tempUserId &&
                  m.id !== tempAssistantId &&
                  m.id !== answer.userMessage.id,
              )
              return [...cleaned, answer.userMessage, answer.assistantMessage]
            })
          }

          if (parsed.eventName === 'error') {
            streamError = String(data.message ?? 'Streaming failed')
          }
        }
      }

      if (streamError && !gotDone) throw new Error(streamError)
      await loadConversations()
    } catch (err) {
      if (gotDone) {
        await loadConversations().catch(() => undefined)
        return
      }
      setError(err instanceof Error ? err.message : 'Chat failed')
      setMessages((prev) =>
        prev.filter((m) => !(m.id === tempAssistantId && m.content === '')),
      )
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[280px_1fr]">
      <Card className="h-[calc(100vh-12rem)]">
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
          <CardTitle className="text-base">Conversations</CardTitle>
          <Button size="sm" variant="outline" onClick={() => void createConversation()}>
            <MessageSquarePlus className="h-4 w-4" />
            New
          </Button>
        </CardHeader>
        <CardContent className="p-0">
          <ScrollArea className="h-[calc(100vh-16rem)] px-3 pb-3">
            <div className="space-y-1">
              {conversations.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => setActiveId(c.id)}
                  className={cn(
                    'w-full rounded-lg px-3 py-2.5 text-left text-sm transition',
                    activeId === c.id
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'hover:bg-muted text-foreground',
                  )}
                >
                  <div className="line-clamp-2 font-medium">{c.title}</div>
                </button>
              ))}
              {conversations.length === 0 && (
                <p className="px-2 py-6 text-center text-sm text-muted-foreground">
                  No conversations yet.
                </p>
              )}
            </div>
          </ScrollArea>
        </CardContent>
      </Card>

      <Card className="flex h-[calc(100vh-12rem)] flex-col">
        <CardHeader className="flex flex-row items-center justify-between border-b border-border pb-3">
          <div>
            <CardTitle className="text-base">Chat</CardTitle>
            {model && (
              <p className="mt-1 text-xs text-muted-foreground">
                Model <Badge variant="secondary">{model}</Badge>
              </p>
            )}
          </div>
        </CardHeader>

        <CardContent className="flex min-h-0 flex-1 flex-col gap-4 p-4">
          {error && (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </div>
          )}

          {!activeId ? (
            <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
              Select or create a conversation
            </div>
          ) : (
            <>
              <ScrollArea className="min-h-0 flex-1 pr-3">
                <div className="space-y-3 pb-2">
                  {messages.map((m) => (
                    <div
                      key={m.id}
                      className={cn(
                        'max-w-[90%] rounded-2xl px-4 py-3 text-sm shadow-sm',
                        m.role === 'USER'
                          ? 'ml-auto bg-primary text-primary-foreground'
                          : 'mr-auto border border-border bg-card',
                      )}
                    >
                      {m.content ? (
                        <MarkdownMessage
                          content={m.content}
                          inverse={m.role === 'USER'}
                        />
                      ) : sending && m.role === 'ASSISTANT' ? (
                        <span className="inline-flex items-center gap-2 text-muted-foreground">
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          Generating…
                        </span>
                      ) : null}
                      {m.citations?.length > 0 && (
                        <div
                          className={cn(
                            'mt-2 border-t pt-2 text-xs',
                            m.role === 'USER'
                              ? 'border-white/20 text-primary-foreground/80'
                              : 'border-border text-muted-foreground',
                          )}
                        >
                          Sources:{' '}
                          {m.citations
                            .map((c) => {
                              const chunk =
                                typeof c.chunkIndex === 'number'
                                  ? ` · chunk ${c.chunkIndex}`
                                  : ''
                              return `[${c.index}] ${c.sourceFilename}${chunk}`
                            })
                            .join(' · ')}
                        </div>
                      )}
                    </div>
                  ))}
                  <div ref={bottomRef} />
                </div>
              </ScrollArea>

              <form className="flex gap-2" onSubmit={onSend}>
                <Input
                  placeholder="Ask a question…"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  disabled={sending}
                />
                <Button type="submit" disabled={sending || !input.trim()}>
                  {sending ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Send className="h-4 w-4" />
                  )}
                  Send
                </Button>
              </form>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
