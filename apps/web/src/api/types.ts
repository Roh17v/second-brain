export type AuthResponse = {
  accessToken: string
  tokenType: string
  userId: string
  email: string
  name: string
}

/** POST /api/auth/register */
export type RegisterResponse = {
  status: 'VERIFICATION_REQUIRED' | 'AUTHENTICATED'
  email: string
  message: string
  auth: AuthResponse | null
}

export type Workspace = {
  id: string
  name: string
  description: string | null
  ownerId: string
  createdAt: string
  updatedAt: string
}

export type Document = {
  id: string
  workspaceId: string
  ownerId: string
  originalFilename: string
  contentType: string | null
  sizeBytes: number
  status: string
  failureReason: string | null
  createdAt: string
  updatedAt: string
}

export type Conversation = {
  id: string
  workspaceId: string
  title: string
  createdAt: string
  updatedAt: string
}

export type Citation = {
  index: number
  chunkId: string
  documentId: string
  sourceFilename: string
  chunkIndex: number
  score: number
  snippet: string
}

export type ChatMessage = {
  id: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  createdAt: string
  citations: Citation[]
}

export type ConversationDetail = {
  conversation: Conversation
  messages: ChatMessage[]
}

export type ChatAnswer = {
  conversationId: string
  userMessage: ChatMessage
  assistantMessage: ChatMessage
  citations: Citation[]
  model: string
}
