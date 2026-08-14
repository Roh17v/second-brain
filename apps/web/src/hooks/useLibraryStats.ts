import { useQuery } from '@tanstack/react-query'
import { apiRequest } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import type { LibraryStats } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'

export function useLibraryStats() {
  const { token } = useAuth()
  return useQuery({
    queryKey: queryKeys.stats,
    queryFn: () => apiRequest<LibraryStats>('/api/me/stats', {}, token),
    enabled: Boolean(token),
    staleTime: 15_000,
    refetchInterval: (query) => {
      const s = query.state.data
      if (s && s.indexed < s.documents) return 2500
      return false
    },
  })
}
