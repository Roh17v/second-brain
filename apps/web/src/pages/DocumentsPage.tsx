import { FormEvent, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { apiRequest } from '../api/client'
import type { Document } from '../api/types'
import { useAuth } from '../auth/AuthContext'

export default function DocumentsPage() {
  const { workspaceId } = useParams()
  const { token } = useAuth()
  const [documents, setDocuments] = useState<Document[]>([])
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function load() {
    if (!workspaceId) return
    setLoading(true)
    setError(null)
    try {
      const data = await apiRequest<Document[]>(
        `/api/workspaces/${workspaceId}/documents`,
        {},
        token,
      )
      setDocuments(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load documents')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [workspaceId, token])

  async function onUpload(e: FormEvent) {
    e.preventDefault()
    if (!workspaceId || !file) return
    setError(null)
    setBusyId('upload')
    try {
      const body = new FormData()
      body.append('file', file)
      await apiRequest<Document>(
        `/api/workspaces/${workspaceId}/documents`,
        { method: 'POST', body },
        token,
      )
      setFile(null)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setBusyId(null)
    }
  }

  async function processDoc(id: string) {
    if (!workspaceId) return
    setBusyId(id + ':process')
    setError(null)
    try {
      await apiRequest(
        `/api/workspaces/${workspaceId}/documents/${id}/process`,
        { method: 'POST' },
        token,
      )
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Process failed')
    } finally {
      setBusyId(null)
    }
  }

  async function embedDoc(id: string) {
    if (!workspaceId) return
    setBusyId(id + ':embed')
    setError(null)
    try {
      await apiRequest(
        `/api/workspaces/${workspaceId}/documents/${id}/embed`,
        { method: 'POST' },
        token,
      )
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Embed failed')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="stack">
      <h1>Documents</h1>
      {error && <div className="error">{error}</div>}

      <div className="card stack">
        <h2>Upload</h2>
        <form className="stack" onSubmit={onUpload}>
          <input
            type="file"
            accept=".pdf,.txt,.md,.markdown"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          <button className="btn" type="submit" disabled={!file || busyId === 'upload'}>
            {busyId === 'upload' ? 'Uploading…' : 'Upload'}
          </button>
        </form>
        <p className="muted">After upload: Process → Embed before chat retrieval works well.</p>
      </div>

      <div className="card">
        <h2>Files</h2>
        {loading ? (
          <p className="muted">Loading…</p>
        ) : documents.length === 0 ? (
          <p className="muted">No documents yet.</p>
        ) : (
          <ul className="list">
            {documents.map((doc) => (
              <li key={doc.id}>
                <div className="row" style={{ justifyContent: 'space-between' }}>
                  <div>
                    <strong>{doc.originalFilename}</strong>
                    <div className="muted">
                      status: {doc.status} · {(doc.sizeBytes / 1024).toFixed(1)} KB
                    </div>
                  </div>
                  <div className="row">
                    <button
                      className="btn secondary"
                      type="button"
                      disabled={busyId !== null}
                      onClick={() => void processDoc(doc.id)}
                    >
                      Process
                    </button>
                    <button
                      className="btn secondary"
                      type="button"
                      disabled={busyId !== null}
                      onClick={() => void embedDoc(doc.id)}
                    >
                      Embed
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
