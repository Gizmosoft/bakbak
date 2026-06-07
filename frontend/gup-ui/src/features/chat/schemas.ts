import { z } from 'zod';

import { VALIDATION } from '@/constants/validation';

export const sendMessageSchema = z.object({
  content: z
    .string()
    .trim()
    .min(VALIDATION.message.min, 'Message cannot be empty')
    .max(
      VALIDATION.message.max,
      `Message must be at most ${VALIDATION.message.max} characters`
    ),
});

export type SendMessageFormValues = z.infer<typeof sendMessageSchema>;
