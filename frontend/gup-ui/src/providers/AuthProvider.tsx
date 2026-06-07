import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

import { login as loginApi, register as registerApi } from '@/api/auth.api';
import { setTokenGetter, setUnauthorizedHandler } from '@/api/client';
import { getCurrentUser } from '@/api/users.api';
import { clearAuthToken, getAuthToken, setAuthToken } from '@/lib/token-storage';
import { clearStoredDrafts } from '@/lib/draft-storage';
import { queryClient } from '@/providers/QueryProvider';
import type { LoginRequest, RegisterRequest } from '@/types';
import type { UserResponse } from '@/types/user';
import { chatClient } from '@/websocket/chat.client';

type AuthContextValue = {
  user: UserResponse | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const tokenRef = useRef<string | null>(null);

  const clearSession = useCallback(async () => {
    tokenRef.current = null;
    setToken(null);
    setUser(null);
    await clearAuthToken();
    await clearStoredDrafts();
  }, []);

  const applySession = useCallback((nextToken: string, nextUser: UserResponse) => {
    tokenRef.current = nextToken;
    setToken(nextToken);
    setUser(nextUser);
  }, []);

  const bootstrap = useCallback(async () => {
    setIsLoading(true);
    try {
      const storedToken = await getAuthToken();
      if (!storedToken) {
        return;
      }

      tokenRef.current = storedToken;
      setToken(storedToken);

      const currentUser = await getCurrentUser();
      setUser(currentUser);
    } catch {
      await clearSession();
    } finally {
      setIsLoading(false);
    }
  }, [clearSession]);

  useEffect(() => {
    setTokenGetter(() => tokenRef.current);
    setUnauthorizedHandler(async () => {
      chatClient.disconnect();
      queryClient.clear();
      await clearSession();
    });

    void bootstrap();

    return () => {
      setUnauthorizedHandler(null);
    };
  }, [bootstrap, clearSession]);

  const login = useCallback(
    async (request: LoginRequest) => {
      const response = await loginApi(request);
      await setAuthToken(response.token);
      applySession(response.token, response.user);
    },
    [applySession]
  );

  const register = useCallback(
    async (request: RegisterRequest) => {
      const response = await registerApi(request);
      await setAuthToken(response.token);
      applySession(response.token, response.user);
    },
    [applySession]
  );

  const logout = useCallback(async () => {
    // Phase 7: full session teardown — STOMP, cache, SecureStore (token + drafts).
    chatClient.disconnect();
    queryClient.clear();
    await clearSession();
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isLoading,
      isAuthenticated: token !== null && user !== null,
      login,
      register,
      logout,
    }),
    [user, token, isLoading, login, register, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
