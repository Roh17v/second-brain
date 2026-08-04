import { FormEvent, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { apiRequest } from '../api/client'
import type {
  ChatAnswer,
  Conversation,
  ConversationDetail,
  ChatMessage,
  Citation,
} from '../api/types'
import { useAuth } from '../auth/AuthContext'

const API_BASE = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

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
  const logRef = useRef<HTMLDivElement>(null)

  async function loadConversations() {
    if (!workspaceId) return
    const data = await apiRequest<Conversation[]>(
      `/api/workspaces/${workspaceId}/conversations`,
      {},
      token,
    )
    setConversations(data)
    if (!activeId && data.length > 0) {
      setActiveId(data[0].id)
    }
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
    if (logRef.current) {
      logRef.current.scrollTop = logRef.current.scrollHeight
    }
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
      let streamError: string | null = null

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        const parts = buffer.split('\n\n')
        buffer = parts.pop() ?? ''

        for (const part of parts) {
          const lines = part.split('\n')
          let eventName = 'message'
          let dataLine = ''
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventName = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              dataLine += line.slice(5).trim()
            }
          }
          if (!dataLine) continue

          const data = JSON.parse(dataLine) as Record<string, unknown>

          if (eventName === 'user') {
            const userMessage = data.userMessage as ChatMessage
            const citations = (data.citations as Citation[]) || []
            if (data.model) setModel(String(data.model))
            setMessages((prev) =>
              prev.map((m) => {
                if (m.id === tempUserId) {
                  return { ...userMessage, citations: [] }
                }
                if (m.id === tempAssistantId) {
                  return { ...m, citations }
                }
                return m
              }),
            )
          }

          if (eventName === 'token') {
            const delta = String(data.delta ?? '')
            setMessages((prev) =>
              prev.map((m) =>
                m.id === tempAssistantId ? { ...m, content: m.content + delta } : m,
              ),
            )
          }

          if (eventName === 'done') {
            const answer = data as unknown as ChatAnswer
            setModel(answer.model)
            setMessages((prev) => {
              const withoutTemps = prev.filter(
                (m) => m.id !== tempUserId && m.id !== tempAssistantId,
              )
              return [
                ...withoutTemps,
                answer.userMessage,
                answer.assistantMessage,
              ]
            })
          }

          if (eventName === 'error') {
            streamError = String(data.message ?? 'Streaming failed')
          }
        }
      }

      if (streamError) {
        throw new Error(streamError)
      }

      await loadConversations()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Chat failed')
      // Drop empty streaming assistant bubble on failure
      setMessages((prev) =>
        prev.filter((m) => !(m.id === tempAssistantId && m.content === '')),
      )
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h1>Chat</h1>
        <button className="btn" type="button" onClick={() => void createConversation()}>
          New conversation
        </button>
      </div>

      {error && <div className="error">{error}</div>}
      {model && <p className="muted">Last answer model: {model} (streaming)</p>}

      <div className="row" style={{ alignItems: 'flex-start' }}>
        <div className="card" style={{ width: 260 }}>
          <h3>Conversations</h3>
          <ul className="list">
            {conversations.map((c) => (
              <li key={c.id}>
                <button
                  type="button"
                  className="btn secondary"
                  style={{ width: '100%' }}
                  onClick={() => setActiveId(c.id)}
                >
                  {c.title}
                </button>
              </li>
            ))}
          </ul>
          {conversations.length === 0 && (
            <p className="muted">Create a conversation to start asking questions.</p>
          )}
        </div>

        <div className="card" style={{ flex: 1 }}>
          {!activeId ? (
            <p className="muted">Select or create a conversation.</p>
          ) : (
            <>
              <div className="chat-log" ref={logRef}>
                {messages.map((m) => (
                  <div
                    key={m.id}
                    className={`bubble ${m.role === 'USER' ? 'user' : 'assistant'}`}
                  >
                    <div>
                      {m.content || (sending && m.role === 'ASSISTANT' ? '…' : '')}
                    </div>
                    {m.citations?.length > 0 && (
                      <div className="citations">
                        Sources:{' '}
                        {m.citations
                          .map((c) => `[${c.index}] ${c.sourceFilename}`)
                          .join(' · ')}
                      </div>
                    )}
                  </div>
                ))}
              </div>
              <form className="row" onSubmit={onSend}>
                <input
                  className="input"
                  style={{ flex: 1 }}
                  placeholder="Ask about your knowledge…"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  disabled={sending}
                />
                <button className="btn" type="submit" disabled={sending || !input.trim()}>
                  {sending ? 'Streaming…' : 'Send'}
                </button>
              </form>
              <p className="muted">
                Answers stream token-by-token from Ollama. First token can still take a bit.
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
