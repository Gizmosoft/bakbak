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
    presence: (conversationId: number) =>
      `/api/conversations/${conversationId}/participants/presence`,
  },
  inbox: {
    pending: '/api/inbox/pending',
  },
} as const;

export const API_LIMITS = {
  searchDefault: 20,
  searchMax: 100,
} as const;
