import { NavLink, Outlet, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function WorkspaceLayout() {
  const { workspaceId } = useParams()
  const { logout } = useAuth()

  return (
    <div className="app-shell">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <nav className="nav">
          <NavLink to="/">← Workspaces</NavLink>
          <NavLink to={`/workspaces/${workspaceId}/chat`}>Chat</NavLink>
          <NavLink to={`/workspaces/${workspaceId}/documents`}>Documents</NavLink>
        </nav>
        <button className="btn secondary" type="button" onClick={logout}>
          Log out
        </button>
      </div>
      <Outlet />
    </div>
  )
}
