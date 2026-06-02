export const API_PATHS = {
  auth: {
    register: '/api/auth/register',
    login: '/api/auth/login',
  },
  users: {
    me: '/api/users/me',
    search: '/api/users/search',
  },
  conversations: {
    list: '/api/conversations',
    messages: (conversationId: number) =>
      `/api/conversations/${conversationId}/messages`,
  },
} as const;

export const API_LIMITS = {
  searchDefault: 20,
  searchMax: 100,
  messagesDefault: 50,
  messagesMax: 100,
} as const;
