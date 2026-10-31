# Convenciones REST — infraestructura #24 y contratos futuros

## Estado efectivo

La base técnica de #24 está implementada para revisión; ADR-014 y reglas fiscales conservan Proposed. Contrato implementado: [openapi-v1.yaml](openapi-v1.yaml), sólo información pública. [Guía de implementación y límites](foundation-24.md). Las rutas de identidad/tenant/comandos descritas abajo son propuestas, no funcionalidades realizadas.

## Convenciones ejecutables de infraestructura

- DTO request/response records, camelCase, separados de persistencia; `@Valid` y constraints servidor. No hay ORM ni modelos fiscales.
- JSON malformado/tipos incompatibles/propiedades desconocidas/UUID inválido: 400. DTO bien formado que incumple constraints: 422. No coerción silenciosa de strings a enteros, fracciones a enteros o números/booleanos a texto.
- UUID completo 8-4-4-4-12; se rechazan abreviaturas aceptadas por UUID.fromString. Correlación: UUID generado por servidor en X-Correlation-Id, sin confiar en el encabezado entrante.
- `PageQuery.parse`: page base cero default 0, máximo 10000; size default 20, rango 1–100; sort único `campo,asc|desc`, default `id,asc`, whitelist por endpoint. Repositorios futuros aplican scopes y orden secundario único por id antes de paginar; nunca concatenar sort a SQL. `PageResponse<T>` devuelve items/page/size/hasNext, sin totales ficticios ni count automático.
- `@ExpectedVersion`: entero requerido 0–9007199254740991, exacto en Java long/JavaScript. Validar sintaxis no comprueba versión persistida: esa comparación transaccional corresponde a cada servicio futuro.
- `ApiIdentifiers.idempotencyKey`: sólo UUID sintáctico. Cada mutación futura necesita scope tenant/actor/operación autorizado, fingerprint canónico, persistencia/replay atómicos, retención y tests concurrentes antes de prometer idempotencia durable. No se crea tabla/replay en #24; GET público no requiere clave.
- Instantes UTC ISO 8601 con Z (`Instant`), fechas civiles YYYY-MM-DD (`LocalDate`); no inferir zona/plazo fiscal. Importes como decimal string con moneda ISO 4217 explícita; escala/signo/redondeo por contrato revisado, no regla fiscal global inventada.

Errores RFC 9457 con type/title/status/detail/instance/code/traceId/fieldErrors y Cache-Control: no-store. `type=urn:fiscuvia:problem:<código-en-minúsculas-con-guiones>`; `instance=urn:uuid:<traceId>` evita reflejar URI/query/IDs enviados. Mensajes fijos ES-MX; fieldErrors sólo field/code/message, máximo 20, sin valores rechazados ni mensajes interpolados de constraints.

| HTTP | Códigos preparados |
|---|---|
| 400 | BAD_REQUEST, INVALID_PAGINATION |
| 401 / 403 / 404 | AUTHENTICATION_REQUIRED / ACCESS_DENIED / NOT_FOUND |
| 405 / 406 | METHOD_NOT_ALLOWED / NOT_ACCEPTABLE |
| 409 | CONFLICT, STALE_VERSION, IDEMPOTENCY_KEY_REUSED |
| 413 / 415 | PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE |
| 422 / 429 / 500 | VALIDATION_FAILED / RATE_LIMITED / INTERNAL_ERROR |

#27 usa sesión BFF: 401 no anuncia Bearer (no se aceptan tokens Bearer). 405 preserva Allow cuando la solicitud alcanza MVC; una mutación sin CSRF es rechazada antes con 403. No se exponen encabezados arbitrarios de excepciones. Los filtros OIDC usan el puerto HttpProblemResponder y SafeErrorController conserva el fallback seguro; los redirects del protocolo conducen a rutas de UI fijas, sin errores privados. Rate limiting y reglas de dominio no se declaran operativos por disponer de sus códigos.

## Propuestas de negocio y siguientes slices

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
