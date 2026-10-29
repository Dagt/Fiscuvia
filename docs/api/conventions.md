# Convenciones REST — Fiscuvia v0.1 (Proposed)

## Autenticación
OIDC provider escogido por ADR. Verificar token/sesión y obtener user_id interno. No confiar en `tenantId`, rol ni `taxpayerId` de peticiones sin lookup y autorización; usuario global sin permisos hasta membresía activa.

## URLs
`GET /api/v1/me`; recursos del despacho bajo `/api/v1/tenants/{tenantId}` donde tenantId se confirma servidor antes de establecer contexto RLS. Portal de contribuyente bajo rutas propias y alcance delegado. Rutas genéricas para datos del operador separadas de la app fiscal.

## Respuesta
`application/json`, camelCase, UUID strings, timestamps ISO-8601 UTC y fechas ISO YYYY-MM-DD, números monetarios decimal string (o schema decimal definido), moneda MXN explícita. DTO nunca expone entity JPA.

## Estado y concurrencia
Aggregate mutable incluye `id`, `version`, `createdAt`, `updatedAt`; comandos con `expectedVersion` cuando dependen de estado. Conflicto `409 STALE_VERSION`; no sobrescribir silenciosamente.

## Idempotencia
Header `Idempotency-Key: uuid` en alta recurrente, generación por periodo, finalización de upload, cobros, auditorías y futuras descargas SAT. Misma clave+contenido retorna mismo resultado lógico; clave reutilizada con cuerpo diferente 409.

## Acciones de dominio
No usar PATCH para `{"filingState":"FILED"}` o `{"taxPaymentState":"PAID"}`. Usar explícitamente:
`POST /obligation-occurrences/{id}/reviews`,
`POST /obligation-occurrences/{id}/filings`,
`POST /obligation-occurrences/{id}/tax-payments`,
`POST /obligation-occurrences/{id}/reopenings`.
Cada comando valida estado y capacidad y escribe audit.

## Status y errores
200 leído/acción, 201 creación, 202 trabajo genuinamente asíncrono, 204 resultado vacío. Error `application/problem+json` conforme RFC 9457 con extensiones `code, traceId, fieldErrors` seguras; 400 sintaxis, 401 sesión, 403 permiso o 404 para no filtrar existencia, 409 conflicto, 422 regla de dominio, 429 cuota, 500 error. Nunca tokens, PII o stack trace.

## Listas
Paginar desde backend con `page/size/sort` (size máximo definido), filtros whitelist, orden estable, scope de tenant y cartera en query, totales cuando eficientes. Reportes/export respetan mismos scopes.

## Files
Inicializar upload -> PUT firmado privado -> finalize valida checksum/size/MIME/scan -> autorizar download -> URL firmada temporal. No aceptar URL arbitraria enviada por cliente para SSRF.

## API compatibility
Publicar contrato versionado, pruebas integrales de schemas, no romper clientes silenciosamente; documentar cambios API en PR y migración de frontend. OpenAPI inicial es parcial y no debe afirmarse completo.
