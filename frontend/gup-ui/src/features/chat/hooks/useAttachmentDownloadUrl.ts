import { useQuery } from '@tanstack/react-query';

import { getAttachmentDownloadUrl } from '@/api/attachments.api';
import { queryKeys } from '@/constants/query-keys';

export function useAttachmentDownloadUrl(attachmentId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.attachmentDownloadUrl(attachmentId ?? ''),
    queryFn: () => getAttachmentDownloadUrl(attachmentId!),
    enabled: Boolean(attachmentId),
    staleTime: 10 * 60 * 1000,
    gcTime: 15 * 60 * 1000,
  });
}
