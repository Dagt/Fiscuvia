export interface paths {
  '/public/info': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    /** Consultar información pública de Fiscuvia */
    get: operations['getPlatformInfo']
    put?: never
    post?: never
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
}
export type webhooks = Record<string, never>
export interface components {
  schemas: {
    /** @description Versión exacta para Java long y JavaScript; una versión obsoleta produce 409 STALE_VERSION */
    ExpectedVersion: number
    FieldError: {
      /** @enum {string} */
      code: 'REQUIRED' | 'SIZE' | 'FORMAT' | 'RANGE' | 'INVALID'
      field: string
      message: string
    }
    PageMetadata: {
      hasNext: boolean
      page: number
      size: number
    }
    PlatformInfo: {
      /** @constant */
      apiVersion: '1.0.0'
      /** @constant */
      productName: 'Fiscuvia'
      /** @constant */
      tagline: 'Todo tu despacho, una sola vía.'
    }
    /**
     * @description RFC 9457. Mensajes españoles fijos y seguros, sin valores rechazados, rutas, consultas SQL ni datos de infraestructura. El catálogo de códigos es infraestructura, no implementación de autenticación, reglas de negocio o rate limiting.
     * @example {
     *       "type": "urn:fiscuvia:problem:validation-failed",
     *       "title": "Datos inválidos",
     *       "status": 422,
     *       "detail": "Revisa los campos indicados antes de continuar.",
     *       "instance": "urn:uuid:00000000-0000-4000-8000-000000000001",
     *       "code": "VALIDATION_FAILED",
     *       "traceId": "00000000-0000-4000-8000-000000000001",
     *       "fieldErrors": [
     *         {
     *           "field": "displayName",
     *           "code": "REQUIRED",
     *           "message": "Este campo es obligatorio."
     *         }
     *       ]
     *     }
     */
    ProblemDetails: {
      /** @enum {string} */
      code:
        | 'BAD_REQUEST'
        | 'INVALID_PAGINATION'
        | 'AUTHENTICATION_REQUIRED'
        | 'ACCESS_DENIED'
        | 'NOT_FOUND'
        | 'METHOD_NOT_ALLOWED'
        | 'NOT_ACCEPTABLE'
        | 'CONFLICT'
        | 'STALE_VERSION'
        | 'IDEMPOTENCY_KEY_REUSED'
        | 'PAYLOAD_TOO_LARGE'
        | 'UNSUPPORTED_MEDIA_TYPE'
        | 'VALIDATION_FAILED'
        | 'RATE_LIMITED'
        | 'INTERNAL_ERROR'
      detail: string
      fieldErrors: components['schemas']['FieldError'][]
      /** Format: uri */
      instance: string
      /** @enum {integer} */
      status:
        400 | 401 | 403 | 404 | 405 | 406 | 409 | 413 | 415 | 422 | 429 | 500
      title: string
      /** Format: uuid */
      traceId: string
      /** Format: uri */
      type: string
    }
  }
  responses: {
    /** @description Sintaxis, tipo o parámetro inválido (400) */
    BadRequest: {
      headers: {
        [name: string]: unknown
      }
      content: {
        'application/problem+json': components['schemas']['ProblemDetails']
      }
    }
    /** @description Conflicto, versión obsoleta o clave reutilizada (409) */
    Conflict: {
      headers: {
        [name: string]: unknown
      }
      content: {
        'application/problem+json': components['schemas']['ProblemDetails']
      }
    }
    /** @description Operación no permitida (403); autorización futura */
    Forbidden: {
      headers: {
        [name: string]: unknown
      }
      content: {
        'application/problem+json': components['schemas']['ProblemDetails']
      }
    }
    /** @description Error interno con mensaje seguro (500) */
    InternalError: {
      headers: {
        [name: string]: unknown
      }
      content: {
        'application/problem+json': components['schemas']['ProblemDetails']
      }
    }
    /** @description Formato de respuesta no admitido (406) */
    NotAcceptable: {
      headers: {
        [name: string]: unknown
      }
      content: {
        'application/problem+json': components['schemas']['ProblemDetails']
      }
    }
    /** @description Recurso no disponible (404); no confirma existencia entre despachos */
    NotFound: {
      headers: {
        [name: string]: unknown
      }
      content: {
        'application/problem+json': components['schemas']['ProblemDetails']
      }
    }
    /** @description Límite de solicitudes (429); enforcement futuro */
    RateLimited: {
      headers: {
        [name: string]: unknown
      }
      content: {
        'application/problem+json': components['schemas']['ProblemDetails']
      }
    }
    /** @description Autenticación requerida (401); integración OIDC futura */
    Unauthorized: {
      headers: {
        'WWW-Authenticate'?: 'Bearer'
        [name: string]: unknown
      }
      content: {
        'application/problem+json': components['schemas']['ProblemDetails']
      }
    }
    /** @description DTO bien formado pero inválido, o regla de dominio futura (422) */
    Validation: {
      headers: {
        [name: string]: unknown
      }
      content: {
        'application/problem+json': components['schemas']['ProblemDetails']
      }
    }
  }
  parameters: {
    /** @description Convención futura: UUID sintáctico. Cada mutación debe implementar scope autorizado, huella canónica, persistencia/replay y retención antes de prometer idempotencia. No se requiere ni se utiliza en GET /public/info. */
    IdempotencyKey: string
    Page: number
    Size: number
    /** @description El endpoint debe restringir campos; orden secundario único por id en el repositorio */
    Sort: string
  }
  requestBodies: never
  headers: {
    /** @description UUID generado por el servidor; no refleja encabezados arbitrarios del solicitante */
    CorrelationId: string
  }
  pathItems: never
}
export type $defs = Record<string, never>
export interface operations {
  getPlatformInfo: {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    requestBody?: never
    responses: {
      /** @description Identidad pública del producto; sin datos de usuarios ni infraestructura */
      200: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content: {
          /**
           * @example {
           *       "productName": "Fiscuvia",
           *       "tagline": "Todo tu despacho, una sola vía.",
           *       "apiVersion": "1.0.0"
           *     }
           */
          'application/json': components['schemas']['PlatformInfo']
        }
      }
      406: components['responses']['NotAcceptable']
      500: components['responses']['InternalError']
    }
  }
}
