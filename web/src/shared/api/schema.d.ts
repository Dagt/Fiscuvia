export interface paths {
  '/auth/authorize/local': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    /** Redirect OIDC gestionado por Spring Security; requiere aprobación de login POST+CSRF de un uso. */
    get: operations['redirectToLocalOidc']
    put?: never
    post?: never
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/callback/local': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    /** Callback OIDC gestionado por Spring Security; valida state/nonce/PKCE y token. No es un endpoint de identidad genérico. */
    get: operations['completeLocalOidc']
    put?: never
    post?: never
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/csrf': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    /** Obtener token CSRF de sesión */
    get: operations['getCsrfToken']
    put?: never
    post?: never
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/invitations': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    get?: never
    put?: never
    /** Crear invitación local para issuer/subject exacto; owner con MFA reciente */
    post: operations['createInvitation']
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/invitations/accept': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    get?: never
    put?: never
    /** Consumir invitación atómica de un solo uso */
    post: operations['acceptInvitation']
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/invitations/revoke': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    get?: never
    put?: never
    /** Revocar invitación no consumida */
    post: operations['revokeInvitation']
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/login': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    get?: never
    put?: never
    /**
     * Iniciar login OIDC real con PKCE
     * @description Inicio BFF real: enviar X-CSRF-TOKEN o el campo _csrf del formulario. Debe existir un token CSRF válido de la misma sesión. No acepta un returnUrl arbitrario.
     */
    post: operations['beginLogin']
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/logout': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    get?: never
    put?: never
    /** Invalidar sesión local */
    post: operations['logout']
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/memberships/status': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    get?: never
    put?: never
    /** Suspender o revocar membresía no propietaria; owner con MFA reciente */
    post: operations['changeMembershipStatus']
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/status': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    /** Consultar disponibilidad del emisor local */
    get: operations['getAuthStatus']
    put?: never
    post?: never
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/auth/tenant': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    get?: never
    put?: never
    /** Validar membresía activa del despacho */
    post: operations['authorizeTenant']
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
  '/me': {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    /** Consultar identidad y membresías activas autorizadas */
    get: operations['getCurrentIdentity']
    put?: never
    post?: never
    delete?: never
    options?: never
    head?: never
    patch?: never
    trace?: never
  }
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
  '/tenants/{tenantId}/settings': {
    parameters: {
      query?: never
      header?: never
      path: {
        /** @description Candidato; cada operación verifica sesión, membresía, capacidad y estado. Un UUID no concede acceso. */
        tenantId: string
      }
      cookie?: never
    }
    /** Consultar configuración del despacho autorizado */
    get: operations['getFirmSettings']
    put?: never
    /**
     * Actualizar configuración con capacidad explícita y MFA reciente
     * @description Sesión, CSRF, membresía ACTIVE, despacho ACTIVE, TENANT_SETTINGS_MANAGE explícito y MFA verificable. Sin política aprobada permanece DENY. No permite cambiar owner, estado, logo, rol ni tenant. Idempotencia durable por tenant/actor/comando/huella; reintento idéntico devuelve la versión aplicada, incluso si hubo cambios posteriores. Volver a consultar GET para obtener el estado actual.
     */
    post: operations['updateFirmSettings']
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
    AcceptedInvitation: {
      /** Format: uuid */
      tenantId: string
    }
    AcceptInvitation: {
      token: string
    }
    AuthStatus: {
      loginAvailable: boolean
    }
    CsrfToken: {
      /** @constant */
      headerName: 'X-CSRF-TOKEN'
      token: string
    }
    CurrentIdentity: {
      memberships: components['schemas']['Membership'][]
      privilegedMfaVerified: boolean
      /** Format: uuid */
      userId: string
    }
    /** @description Versión exacta para Java long y JavaScript; una versión obsoleta produce 409 STALE_VERSION */
    ExpectedVersion: number
    FieldError: {
      /** @enum {string} */
      code: 'REQUIRED' | 'SIZE' | 'FORMAT' | 'RANGE' | 'INVALID'
      field: string
      message: string
    }
    FirmSettings: {
      canManage: boolean
      commercialName: string | null
      /** Format: email */
      contactEmail: string | null
      /** Format: date-time */
      createdAt: string
      /** @constant */
      locale: 'es-MX'
      /** @description Sin almacenamiento privado aprobado; no admite uploads, URLs ni paths. Fallback visual por nombre. */
      logoReference: null
      phone: string | null
      /** @enum {string} */
      status: 'ACTIVE' | 'SUSPENDED'
      timeZone: components['schemas']['FirmTimeZone']
      /** Format: date-time */
      updatedAt: string
      version: number
    }
    FirmSettingsUpdated: {
      version: number
    }
    /** @enum {string} */
    FirmTimeZone:
      | 'America/Mexico_City'
      | 'America/Cancun'
      | 'America/Chihuahua'
      | 'America/Ciudad_Juarez'
      | 'America/Hermosillo'
      | 'America/Mazatlan'
      | 'America/Monterrey'
      | 'America/Tijuana'
    Invitation: {
      /** Format: date-time */
      expiresAt: string
      /** Format: uuid */
      invitationId: string
      token: string
    }
    InviteRequest: {
      /** @enum {string} */
      role: 'FIRM_SUPERVISOR' | 'ACCOUNTANT' | 'ASSISTANT'
      subject: string
      /** Format: uuid */
      tenantId: string
    }
    Membership: {
      /** @description Nombre persistido autorizado; null en raíces anteriores aún sin configurar. Nunca un nombre inventado. */
      commercialName?: string | null
      /** Format: uuid */
      membershipId: string
      role: components['schemas']['MembershipRole']
      /** Format: uuid */
      tenantId: string
      /** @enum {string} */
      tenantStatus?: 'ACTIVE' | 'SUSPENDED'
    }
    /** @enum {string} */
    MembershipRole:
      'FIRM_OWNER_ADMIN' | 'FIRM_SUPERVISOR' | 'ACCOUNTANT' | 'ASSISTANT'
    MembershipStatusRequest: {
      /** Format: uuid */
      membershipId: string
      /** @enum {string} */
      status: 'SUSPENDED' | 'REVOKED'
      /** Format: uuid */
      tenantId: string
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
    RevokeInvitation: {
      /** Format: uuid */
      invitationId: string
      /** Format: uuid */
      tenantId: string
    }
    TenantRequest: {
      /** Format: uuid */
      tenantId: string
    }
    UpdateFirmSettings: {
      commercialName: string
      /** Format: email */
      contactEmail?: string | null
      expectedVersion: number
      /** @constant */
      locale: 'es-MX'
      phone?: string | null
      timeZone: components['schemas']['FirmTimeZone']
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
    /** @description CSRF inválido, membresía/rol/MFA insuficiente o invitación rechazada (403); no confirma existencia. */
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
    /** @description Sesión ausente, inválida o expirada (401). El BFF no acepta Bearer tokens. */
    Unauthorized: {
      headers: {
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
  redirectToLocalOidc: {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    requestBody?: never
    responses: {
      /** @description Redirect fijo al IdP local, o a /app o /login?error=autenticacion después del callback; nunca devuelve tokens. */
      302: {
        headers: {
          /** @description Destino de protocolo validado en configuración local. */
          Location?: string
          [name: string]: unknown
        }
        content?: never
      }
      401: components['responses']['Unauthorized']
      500: components['responses']['InternalError']
    }
  }
  completeLocalOidc: {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    requestBody?: never
    responses: {
      /** @description Redirect fijo al IdP local, o a /app o /login?error=autenticacion después del callback; nunca devuelve tokens. */
      302: {
        headers: {
          /** @description Destino de protocolo validado en configuración local. */
          Location?: string
          [name: string]: unknown
        }
        content?: never
      }
      401: components['responses']['Unauthorized']
      500: components['responses']['InternalError']
    }
  }
  getCsrfToken: {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    requestBody?: never
    responses: {
      /** @description Obtener token CSRF de sesión */
      200: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content: {
          'application/json': components['schemas']['CsrfToken']
        }
      }
      400: components['responses']['BadRequest']
      403: components['responses']['Forbidden']
      500: components['responses']['InternalError']
    }
  }
  createInvitation: {
    parameters: {
      query?: never
      header: {
        /** @description Token CSRF de la sesión same-origin; no es un token de identidad. */
        'X-CSRF-TOKEN': string
      }
      path?: never
      cookie?: never
    }
    requestBody: {
      content: {
        'application/json': components['schemas']['InviteRequest']
      }
    }
    responses: {
      /** @description Crear invitación local para issuer/subject exacto; owner con MFA reciente */
      201: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content: {
          'application/json': components['schemas']['Invitation']
        }
      }
      400: components['responses']['BadRequest']
      401: components['responses']['Unauthorized']
      403: components['responses']['Forbidden']
      422: components['responses']['Validation']
      500: components['responses']['InternalError']
    }
  }
  acceptInvitation: {
    parameters: {
      query?: never
      header: {
        /** @description Token CSRF de la sesión same-origin; no es un token de identidad. */
        'X-CSRF-TOKEN': string
      }
      path?: never
      cookie?: never
    }
    requestBody: {
      content: {
        'application/json': components['schemas']['AcceptInvitation']
      }
    }
    responses: {
      /** @description Consumir invitación atómica de un solo uso */
      200: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content: {
          'application/json': components['schemas']['AcceptedInvitation']
        }
      }
      400: components['responses']['BadRequest']
      401: components['responses']['Unauthorized']
      403: components['responses']['Forbidden']
      422: components['responses']['Validation']
      500: components['responses']['InternalError']
    }
  }
  revokeInvitation: {
    parameters: {
      query?: never
      header: {
        /** @description Token CSRF de la sesión same-origin; no es un token de identidad. */
        'X-CSRF-TOKEN': string
      }
      path?: never
      cookie?: never
    }
    requestBody: {
      content: {
        'application/json': components['schemas']['RevokeInvitation']
      }
    }
    responses: {
      /** @description Revocar invitación no consumida */
      204: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content?: never
      }
      400: components['responses']['BadRequest']
      401: components['responses']['Unauthorized']
      403: components['responses']['Forbidden']
      422: components['responses']['Validation']
      500: components['responses']['InternalError']
    }
  }
  beginLogin: {
    parameters: {
      query?: never
      header?: {
        /** @description Token CSRF de la sesión same-origin; no es un token de identidad. */
        'X-CSRF-TOKEN'?: string
      }
      path?: never
      cookie?: never
    }
    requestBody?: {
      content: {
        'application/x-www-form-urlencoded': {
          _csrf: string
        }
      }
    }
    responses: {
      /** @description Iniciar login OIDC real con PKCE */
      303: {
        headers: {
          /** @description Destino fijo del inicio OIDC */
          Location?: '/api/v1/auth/authorize/local'
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content?: never
      }
      400: components['responses']['BadRequest']
      403: components['responses']['Forbidden']
      500: components['responses']['InternalError']
    }
  }
  logout: {
    parameters: {
      query?: never
      header: {
        /** @description Token CSRF de la sesión same-origin; no es un token de identidad. */
        'X-CSRF-TOKEN': string
      }
      path?: never
      cookie?: never
    }
    requestBody?: never
    responses: {
      /** @description Invalidar sesión local */
      204: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content?: never
      }
      400: components['responses']['BadRequest']
      401: components['responses']['Unauthorized']
      403: components['responses']['Forbidden']
      500: components['responses']['InternalError']
    }
  }
  changeMembershipStatus: {
    parameters: {
      query?: never
      header: {
        /** @description Token CSRF de la sesión same-origin; no es un token de identidad. */
        'X-CSRF-TOKEN': string
      }
      path?: never
      cookie?: never
    }
    requestBody: {
      content: {
        'application/json': components['schemas']['MembershipStatusRequest']
      }
    }
    responses: {
      /** @description Suspender o revocar membresía no propietaria; owner con MFA reciente */
      204: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content?: never
      }
      400: components['responses']['BadRequest']
      401: components['responses']['Unauthorized']
      403: components['responses']['Forbidden']
      422: components['responses']['Validation']
      500: components['responses']['InternalError']
    }
  }
  getAuthStatus: {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    requestBody?: never
    responses: {
      /** @description Consultar disponibilidad del emisor local */
      200: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content: {
          'application/json': components['schemas']['AuthStatus']
        }
      }
      400: components['responses']['BadRequest']
      403: components['responses']['Forbidden']
      500: components['responses']['InternalError']
    }
  }
  authorizeTenant: {
    parameters: {
      query?: never
      header: {
        /** @description Token CSRF de la sesión same-origin; no es un token de identidad. */
        'X-CSRF-TOKEN': string
      }
      path?: never
      cookie?: never
    }
    requestBody: {
      content: {
        'application/json': components['schemas']['TenantRequest']
      }
    }
    responses: {
      /** @description Validar membresía activa del despacho */
      200: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content: {
          'application/json': components['schemas']['Membership']
        }
      }
      400: components['responses']['BadRequest']
      401: components['responses']['Unauthorized']
      403: components['responses']['Forbidden']
      422: components['responses']['Validation']
      500: components['responses']['InternalError']
    }
  }
  getCurrentIdentity: {
    parameters: {
      query?: never
      header?: never
      path?: never
      cookie?: never
    }
    requestBody?: never
    responses: {
      /** @description Consultar identidad y membresías activas autorizadas */
      200: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content: {
          'application/json': components['schemas']['CurrentIdentity']
        }
      }
      400: components['responses']['BadRequest']
      401: components['responses']['Unauthorized']
      403: components['responses']['Forbidden']
      500: components['responses']['InternalError']
    }
  }
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
  getFirmSettings: {
    parameters: {
      query?: never
      header?: never
      path: {
        /** @description Candidato; cada operación verifica sesión, membresía, capacidad y estado. Un UUID no concede acceso. */
        tenantId: string
      }
      cookie?: never
    }
    requestBody?: never
    responses: {
      /** @description Configuración propia; canManage es presentación, nunca una autorización reutilizable. */
      200: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content: {
          'application/json': components['schemas']['FirmSettings']
        }
      }
      400: components['responses']['BadRequest']
      401: components['responses']['Unauthorized']
      403: components['responses']['Forbidden']
      500: components['responses']['InternalError']
    }
  }
  updateFirmSettings: {
    parameters: {
      query?: never
      header: {
        /** @description Convención futura: UUID sintáctico. Cada mutación debe implementar scope autorizado, huella canónica, persistencia/replay y retención antes de prometer idempotencia. No se requiere ni se utiliza en GET /public/info. */
        'Idempotency-Key': components['parameters']['IdempotencyKey']
        'X-CSRF-TOKEN': string
      }
      path: {
        /** @description Candidato; cada operación verifica sesión, membresía, capacidad y estado. Un UUID no concede acceso. */
        tenantId: string
      }
      cookie?: never
    }
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateFirmSettings']
      }
    }
    responses: {
      /** @description Versión aplicada; cambio, journal, outbox y recibo confirmados atómicamente. */
      200: {
        headers: {
          'X-Correlation-Id': components['headers']['CorrelationId']
          [name: string]: unknown
        }
        content: {
          'application/json': components['schemas']['FirmSettingsUpdated']
        }
      }
      400: components['responses']['BadRequest']
      401: components['responses']['Unauthorized']
      403: components['responses']['Forbidden']
      409: components['responses']['Conflict']
      422: components['responses']['Validation']
      500: components['responses']['InternalError']
    }
  }
}
