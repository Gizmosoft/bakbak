import { useQuery } from '@tanstack/react-query';

import { searchUsers } from '@/api/users.api';
import { queryKeys } from '@/constants/query-keys';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';

export function useUserSearch(query: string) {
  const debouncedQuery = useDebouncedValue(query.trim(), 300);

  return useQuery({
    queryKey: queryKeys.userSearch(debouncedQuery),
    queryFn: () => searchUsers({ q: debouncedQuery }),
    enabled: debouncedQuery.length > 0,
  });
}
