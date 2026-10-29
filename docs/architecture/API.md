# Convenciones API
- REST /api/v1 JSON; contrato OpenAPI versionado, tests de conformidad.
- OIDC con sesión/tokens seguros; si se usan cookies, CSRF y SameSite adecuados; CORS allowlist; nunca guardar tokens sensibles en localStorage.
- DTO separados de entidades JPA, validación del servidor obligatoria, roles/tenant por recurso.
- UUID; paginación acotada, filtros y orden consistentes; no traer todo a memoria.
- Error estándar {code,message,correlationId,fieldErrors?}; sin stack trace, secretos ni PII.
- Idempotency-Key en mutaciones vulnerables a reintentos; versionado/optimistic locking para edición concurrente.
- ISO 8601 UTC para instantes, YYYY-MM-DD para fechas; MXN como decimal string, servidor verifica precisión y redondeo.
- Archivos: streaming, límites de tamaño, MIME real, antivirus/escáner según política, protección XXE/zip bombs y URLs firmadas.
- Rate limits por usuario/tenant, sobre todo login/invitaciones/exportaciones. Probar contrato, errores y autorización.
