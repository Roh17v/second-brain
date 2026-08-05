import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { ThemeProvider } from './theme/ThemeProvider'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import HomePage from './pages/HomePage'
import CollectionsPage from './pages/CollectionsPage'
import SettingsPage from './pages/SettingsPage'
import WorkspaceLayout from './pages/WorkspaceLayout'
import ChatPage from './pages/ChatPage'
import DocumentsPage from './pages/DocumentsPage'

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { token } = useAuth()
  if (!token) {
    return <Navigate to="/login" replace />
  }
  return children
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route
            path="/"
            element={
              <ProtectedRoute>
                <HomePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/collections"
            element={
              <ProtectedRoute>
                <CollectionsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute>
                <SettingsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/collections/:workspaceId"
            element={
              <ProtectedRoute>
                <WorkspaceLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<Navigate to="documents" replace />} />
            <Route path="chat" element={<ChatPage />} />
            <Route path="documents" element={<DocumentsPage />} />
          </Route>

          {/* Legacy redirects */}
          <Route path="/workspaces" element={<Navigate to="/collections" replace />} />
          <Route
            path="/workspaces/:workspaceId/*"
            element={<LegacyWorkspaceRedirect />}
          />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </ThemeProvider>
  )
}

function LegacyWorkspaceRedirect() {
  const { workspaceId, '*': rest } = useParams()
  const suffix = rest ? `/${rest}` : '/documents'
  return <Navigate to={`/collections/${workspaceId}${suffix}`} replace />
}
