// Generado desde docs/api/openapi-v1.yaml. No editar manualmente.
import type { components } from './schema'
export const PLATFORM_INFO = {
  productName: 'Fiscuvia',
  tagline: 'Todo tu despacho, una sola vía.',
  apiVersion: '1.0.0',
} as const satisfies components['schemas']['PlatformInfo']
export const ERROR_STATUS = {
  BAD_REQUEST: 400,
  INVALID_PAGINATION: 400,
  AUTHENTICATION_REQUIRED: 401,
  ACCESS_DENIED: 403,
  NOT_FOUND: 404,
  METHOD_NOT_ALLOWED: 405,
  NOT_ACCEPTABLE: 406,
  QUOTA_UNAVAILABLE: 409,
  CONFLICT: 409,
  STALE_VERSION: 409,
  IDEMPOTENCY_KEY_REUSED: 409,
  PAYLOAD_TOO_LARGE: 413,
  UNSUPPORTED_MEDIA_TYPE: 415,
  VALIDATION_FAILED: 422,
  RATE_LIMITED: 429,
  INTERNAL_ERROR: 500,
} as const satisfies Record<
  components['schemas']['ProblemDetails']['code'],
  number
>
