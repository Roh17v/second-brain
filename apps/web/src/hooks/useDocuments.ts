import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import type { Document } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'

function isInFlight(status: string) {
  return status === 'UPLOADED' || status === 'PROCESSING' || status === 'EMBEDDING'
}

export function useDocuments(workspaceId: string | undefined) {
  const { token } = useAuth()
  return useQuery({
    queryKey: queryKeys.documents(workspaceId ?? ''),
    queryFn: () =>
      apiRequest<Document[]>(
        `/api/workspaces/${workspaceId}/documents`,
        {},
        token,
      ),
    enabled: Boolean(token && workspaceId),
    // Poll while any document is still ingesting
    refetchInterval: (query) => {
      const docs = query.state.data
      if (docs?.some((d) => isInFlight(d.status))) return 2500
      return false
    },
  })
}

export function useUploadDocument(workspaceId: string | undefined) {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (file: File) => {
      if (!workspaceId) throw new Error('Missing workspace')
      const body = new FormData()
      body.append('file', file)
      return apiRequest<Document>(
        `/api/workspaces/${workspaceId}/documents`,
        { method: 'POST', body },
        token,
      )
    },
    onSuccess: () => {
      if (!workspaceId) return
      void queryClient.invalidateQueries({
        queryKey: queryKeys.documents(workspaceId),
      })
      // Home dashboard recent docs depend on these lists
      void queryClient.invalidateQueries({ queryKey: queryKeys.workspaces })
    },
  })
}

export function useRetryDocument(workspaceId: string | undefined) {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentId: string) => {
      if (!workspaceId) throw new Error('Missing workspace')
      return apiRequest(
        `/api/workspaces/${workspaceId}/documents/${documentId}/retry`,
        { method: 'POST' },
        token,
      )
    },
    onSuccess: () => {
      if (!workspaceId) return
      void queryClient.invalidateQueries({
        queryKey: queryKeys.documents(workspaceId),
      })
    },
  })
}
