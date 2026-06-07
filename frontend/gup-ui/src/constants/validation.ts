/** Mirrors backend Jakarta validation on auth/message DTOs. */
export const VALIDATION = {
  username: {
    min: 3,
    max: 30,
    pattern: /^[A-Za-z0-9_]+$/,
    patternMessage: 'Username must be alphanumeric + underscore',
  },
  email: { max: 255 },
  password: { min: 8, max: 100 },
  displayName: { max: 100 },
  message: { min: 1, max: 4000 },
} as const;
