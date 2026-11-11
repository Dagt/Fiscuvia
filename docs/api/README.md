# Contratos API — Fiscuvia

**Actualización #31:** el contrato implementado añade configuración del despacho y nombres/estados propios, sin provisioning HTTP de operador. [Modelo, autorización y procedimiento interno](../architecture/TENANT-PROVISIONING.md). El grant permanente de edición está pendiente, DENY por defecto. CI remota ya ejecutó #88; el aviso histórico #79 al final no describe el estado del PR actual. Cada entrega debe aportar sus cinco checks del SHA final.

[openapi-v1.yaml](openapi-v1.yaml) es la fuente implementada OpenAPI 3.1 / versión 1.0.0 para infraestructura #24 e identidad local/test #27. Incluye info pública, /me, CSRF, login/logout, validación de membresía e invitaciones mínimas. [Autenticación y límites](../security/AUTHENTICATION.md). No habilita módulos fiscales ni proveedor de producción.

- [Convenciones y ejemplos seguros](conventions.md).
- [Catálogo: implementado / propuesto / futuro](endpoint-catalog.md).
- [Borrador de negocio](openapi-v1-draft.yaml): **propuesto, no implementado**, no genera tipos del cliente.
- [Arquitectura API](../architecture/API.md), [datos propuestos](../data-model/schema-v0.1.md), [estados propuestos](../state-machines/README.md).

Hay validación servidor, RFC 9457, correlación y primitivas de paginación/versión/clave de idempotencia. Sus códigos 401/403/409/422/429 se prueban mediante controladores exclusivos del classpath de tests; no acreditan reglas de dominio ni rate limiting operativos. #27 añade pruebas OIDC reales aparte; `/api/v1/me` ahora requiere sesión y devuelve 401 sin ella. ADR-014 conserva Proposed; #74 y #28–30 siguen vigentes.

## Generación y verificación

Desde la raíz, Node 24.13.1 / npm 11.8.0:

```sh
npm ci --prefix tools/ci --ignore-scripts
npm run generate:api --prefix tools/ci
npm run openapi --prefix tools/ci
npm test --prefix tools/ci
```

`openapi` valida ambos documentos con Redocly y comprueba la generación determinista de `web/src/shared/api/schema.d.ts` y `contract-metadata.ts`. openapi-typescript 7.13.0 genera tipos; Prettier 3.9.10 aplica el estilo web. AJV 8.20.0 / ajv-formats 3.0.1 valida JSON Schema 2020-12 y formatos, con strict activo; sólo registra la anotación OpenAPI `example` como anotación. No se descargan refs externas ni se envían contratos a servicios de análisis.

Después de `backend/mvnw -f backend/pom.xml -B -ntp clean verify` (Windows: `backend/mvnw.cmd`):

```sh
node tools/ci/contracts.mjs --responses backend/target/contract-responses.json
```

JUnit realiza HTTP real con Spring Boot/PostgreSQL Testcontainers y escribe respuestas sintéticas seguras. AJV exige todos los códigos, concordancia cuerpo/status/header y esquema estricto. Backend CI incorpora este comando; OpenAPI CI comprueba estructura/generación/fixtures negativos. No se publican respuestas crudas como artifacts ni se instala un endpoint Swagger/introspección.

El borrador conserva la composición `Taxpayer.allOf`/`additionalProperties` pendiente de revisión del modelo; no se promueve como contrato válido de negocio. Los tipos implementados la excluyen.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por presupuesto/facturación; seguimiento #79.** El propietario mantiene presupuesto $0: checks locales completos, sin deshabilitar workflows ni reintentar runners.

Consulta técnica #32: GET /api/v1/tenants/{tenantId}/entitlements, sólo con sesión y TENANT_PLAN_VIEW explícito; sin grant por defecto ni mutaciones HTTP de planes. DTO EntitlementUsage y tipos TS generados en el OpenAPI implementado. No hay precios/cobros/endpoints comerciales nuevos. [Autoridad y semántica de NULL](../architecture/TENANT-ENTITLEMENTS.md).


V9 — política limitada aprobada: [referencia del propietario](https://github.com/Dagt/Fiscuvia/pull/90#issuecomment-6798951955). TENANT_PLAN_VIEW sólo owner ACTIVE + MFA ≤10 minutos; trigger productivo de cupos en INSERT/UPDATE ACTIVE, sin cuotas comerciales por defecto. La aceptación conserva el flujo original y confirma reservas sin duplicar consumo; reactivaciones registran journal/outbox. [Detalles y límites](../architecture/TENANT-ENTITLEMENTS.md). No resuelve OQ-007/014/015, ADR-015 completa ni #74.
