import { FormEvent, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { apiRequest } from '../api/client'
import type {
  ChatAnswer,
  Conversation,
  ConversationDetail,
  ChatMessage,
} from '../api/types'
import { useAuth } from '../auth/AuthContext'

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
    if (!workspaceId || !activeId || !input.trim()) return
    setSending(true)
    setError(null)
    try {
      const answer = await apiRequest<ChatAnswer>(
        `/api/workspaces/${workspaceId}/conversations/${activeId}/messages`,
        {
          method: 'POST',
          body: JSON.stringify({ message: input.trim(), topK: 5 }),
        },
        token,
      )
      setMessages((prev) => [...prev, answer.userMessage, answer.assistantMessage])
      setModel(answer.model)
      setInput('')
      await loadConversations()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Chat failed')
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
      {model && <p className="muted">Last answer model: {model}</p>}

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
              <div className="chat-log">
                {messages.map((m) => (
                  <div
                    key={m.id}
                    className={`bubble ${m.role === 'USER' ? 'user' : 'assistant'}`}
                  >
                    <div>{m.content}</div>
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
                  {sending ? 'Thinking…' : 'Send'}
                </button>
              </form>
              <p className="muted">
                First answer can take a while while Ollama generates locally.
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
