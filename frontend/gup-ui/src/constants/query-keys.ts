export const queryKeys = {
  conversations: ['conversations'] as const,
  messages: (conversationId: number) => ['messages', conversationId] as const,
  userSearch: (query: string) => ['users', 'search', query] as const,
};
