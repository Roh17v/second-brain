import { QueryClient } from '@tanstack/react-query'

/**
 * Shared QueryClient for server state (workspaces, documents, …).
 * Client/UI state stays out of here (Zustand later / local useState).
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Revisit dashboard within this window without refetch
      staleTime: 30_000,
      // Avoid surprise refetches while typing / tab focus in dev
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
})
