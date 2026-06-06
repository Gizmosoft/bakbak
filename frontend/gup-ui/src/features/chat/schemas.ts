import { z } from 'zod';

export const sendMessageSchema = z.object({
  content: z
    .string()
    .trim()
    .min(1, 'Message cannot be empty')
    .max(4000, 'Message must be at most 4000 characters'),
});

export type SendMessageFormValues = z.infer<typeof sendMessageSchema>;
