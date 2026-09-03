import { z } from 'zod';

import { VALIDATION } from '@/constants/validation';

export const sendMessageSchema = z
  .object({
    content: z
      .string()
      .trim()
      .max(
        VALIDATION.message.max,
        `Message must be at most ${VALIDATION.message.max} characters`
      ),
    hasAttachment: z.boolean().optional(),
  })
  .superRefine((value, ctx) => {
    const hasContent = value.content.length >= VALIDATION.message.min;
    const hasAttachment = value.hasAttachment === true;
    if (!hasContent && !hasAttachment) {
      ctx.addIssue({
        code: 'custom',
        message: 'Message cannot be empty',
        path: ['content'],
      });
    }
  });

export type SendMessageFormValues = z.infer<typeof sendMessageSchema>;
