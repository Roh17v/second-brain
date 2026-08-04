import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import WorkspacesPage from './pages/WorkspacesPage'
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
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <WorkspacesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/workspaces/:workspaceId"
          element={
            <ProtectedRoute>
              <WorkspaceLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="chat" replace />} />
          <Route path="chat" element={<ChatPage />} />
          <Route path="documents" element={<DocumentsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
