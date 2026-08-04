import { FormEvent, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../api/client'
import type { Workspace } from '../api/types'
import { useAuth } from '../auth/AuthContext'

export default function WorkspacesPage() {
  const { token, name, logout } = useAuth()
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [newName, setNewName] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const data = await apiRequest<Workspace[]>('/api/workspaces', {}, token)
      setWorkspaces(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load workspaces')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [token])

  async function onCreate(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await apiRequest<Workspace>(
        '/api/workspaces',
        {
          method: 'POST',
          body: JSON.stringify({
            name: newName,
            description: description || null,
          }),
        },
        token,
      )
      setNewName('')
      setDescription('')
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create workspace')
    }
  }

  return (
    <div className="app-shell stack">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <div>
          <h1>Workspaces</h1>
          <p className="muted">Signed in as {name}</p>
        </div>
        <button className="btn secondary" type="button" onClick={logout}>
          Log out
        </button>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="card stack">
        <h2>Create workspace</h2>
        <form className="stack" onSubmit={onCreate}>
          <input
            className="input"
            placeholder="Name (e.g. DSA, System Design)"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            required
          />
          <input
            className="input"
            placeholder="Description (optional)"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
          <button className="btn" type="submit">
            Create
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Your workspaces</h2>
        {loading ? (
          <p className="muted">Loading…</p>
        ) : workspaces.length === 0 ? (
          <p className="muted">No workspaces yet.</p>
        ) : (
          <ul className="list">
            {workspaces.map((ws) => (
              <li key={ws.id} className="row" style={{ justifyContent: 'space-between' }}>
                <div>
                  <strong>{ws.name}</strong>
                  {ws.description && <div className="muted">{ws.description}</div>}
                </div>
                <Link className="btn secondary" to={`/workspaces/${ws.id}/chat`}>
                  Open
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
