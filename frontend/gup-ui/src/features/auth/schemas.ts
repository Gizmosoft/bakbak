import { z } from 'zod';

import { VALIDATION } from '@/constants/validation';

const isoDateRegex = /^\d{4}-\d{2}-\d{2}$/;

export const loginSchema = z.object({
  email: z
    .string()
    .min(1, 'Email is required')
    .email('Invalid email address')
    .max(VALIDATION.email.max, `Email must be at most ${VALIDATION.email.max} characters`),
  password: z
    .string()
    .min(VALIDATION.password.min, `Password must be at least ${VALIDATION.password.min} characters`)
    .max(VALIDATION.password.max, `Password must be at most ${VALIDATION.password.max} characters`),
});

export const registerSchema = z.object({
  username: z
    .string()
    .min(VALIDATION.username.min, `Username must be at least ${VALIDATION.username.min} characters`)
    .max(VALIDATION.username.max, `Username must be at most ${VALIDATION.username.max} characters`)
    .regex(VALIDATION.username.pattern, VALIDATION.username.patternMessage),
  email: z
    .string()
    .min(1, 'Email is required')
    .email('Invalid email address')
    .max(VALIDATION.email.max, `Email must be at most ${VALIDATION.email.max} characters`),
  password: z
    .string()
    .min(VALIDATION.password.min, `Password must be at least ${VALIDATION.password.min} characters`)
    .max(VALIDATION.password.max, `Password must be at most ${VALIDATION.password.max} characters`),
  displayName: z
    .string()
    .max(
      VALIDATION.displayName.max,
      `Display name must be at most ${VALIDATION.displayName.max} characters`
    )
    .optional(),
  dateOfBirth: z
    .string()
    .min(1, 'Date of birth is required')
    .regex(isoDateRegex, 'Use YYYY-MM-DD format')
    .refine((value) => {
      const [year, month, day] = value.split('-').map(Number);
      const date = new Date(year, month - 1, day);
      if (
        date.getFullYear() !== year ||
        date.getMonth() !== month - 1 ||
        date.getDate() !== day
      ) {
        return false;
      }
      return date.getTime() < Date.now();
    }, 'Date of birth must be in the past'),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
export type RegisterFormValues = z.infer<typeof registerSchema>;
