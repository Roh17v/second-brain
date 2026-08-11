import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import type { Workspace } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'

export function useWorkspaces() {
  const { token } = useAuth()
  return useQuery({
    queryKey: queryKeys.workspaces,
    queryFn: () => apiRequest<Workspace[]>('/api/workspaces', {}, token),
    enabled: Boolean(token),
  })
}

export function useWorkspace(workspaceId: string | undefined) {
  const { token } = useAuth()
  return useQuery({
    queryKey: queryKeys.workspace(workspaceId ?? ''),
    queryFn: () =>
      apiRequest<Workspace>(`/api/workspaces/${workspaceId}`, {}, token),
    enabled: Boolean(token && workspaceId),
  })
}

export function useCreateWorkspace() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { name: string; description: string | null }) =>
      apiRequest<Workspace>(
        '/api/workspaces',
        { method: 'POST', body: JSON.stringify(body) },
        token,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.workspaces })
    },
  })
}
