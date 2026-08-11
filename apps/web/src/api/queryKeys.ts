/** Central query keys — keep server-state cache consistent across pages. */
export const queryKeys = {
  workspaces: ['workspaces'] as const,
  workspace: (id: string) => ['workspaces', id] as const,
  documents: (workspaceId: string) => ['documents', workspaceId] as const,
}
