import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import { LoadingState } from '@/components/ui/LoadingState';
import { openDatabase } from '@/db/client';

type DatabaseContextValue = {
  isReady: boolean;
  error: string | null;
};

const DatabaseContext = createContext<DatabaseContextValue | null>(null);

export function DatabaseProvider({ children }: { children: ReactNode }) {
  const [isReady, setIsReady] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    void (async () => {
      try {
        await openDatabase();
        if (!cancelled) {
          setIsReady(true);
        }
      } catch (openError) {
        if (!cancelled) {
          const message =
            openError instanceof Error ? openError.message : 'Failed to open local database';
          setError(message);
          if (__DEV__) {
            console.error('[DatabaseProvider]', openError);
          }
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<DatabaseContextValue>(
    () => ({
      isReady,
      error,
    }),
    [isReady, error]
  );

  if (error) {
    return <LoadingState message="Storage unavailable — restart the app" />;
  }

  if (!isReady) {
    return <LoadingState message="Preparing local storage…" />;
  }

  return <DatabaseContext.Provider value={value}>{children}</DatabaseContext.Provider>;
}

export function useDatabaseContext(): DatabaseContextValue {
  const context = useContext(DatabaseContext);
  if (!context) {
    throw new Error('useDatabaseContext must be used within DatabaseProvider');
  }
  return context;
}
