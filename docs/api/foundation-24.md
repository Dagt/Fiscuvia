# Infraestructura API #24 — implementación revisable

Base main `edbc6ddc1a54e17e5cd38dd108af67cc5eb7e1c7`, PR #78 integrado. Trazabilidad: #24 / épica #3, NFR-020/021, dirección ADR-014 (continúa Proposed). Sin cambios de dominio, tablas, Flyway, RLS, OIDC, observabilidad distribuida ni módulos de negocio. Gate #74 intacto.

## Superficie y arquitectura

Única operación nueva: **GET /api/v1/public/info**. DTO inmutable con productName, tagline y apiVersion. El handler no usa repositorios/DB; el proceso sigue requiriendo PostgreSQL por las guardas de #22. No hay identidad/tenant de demostración. /api/v1/me sigue en 404; /actuator conserva su superficie mínima.

`platform` mantiene allowedDependencies vacío. API pública reutilizable: ApiErrorCode/ApiFailure, ApiIdentifiers, ExpectedVersion, PageQuery/PageResponse. Controladores, binding, correlación y handlers están en `platform.internal.http`. Futuros módulos declaran dependencia explícita de platform y adaptan sus errores de dominio en su propia frontera HTTP; no exponen entidades ni acceden a internals ajenos.

Validación: starter-validation 4.1.2, Hibernate Validator 9.1.5.Final / Jakarta Validation 3.1.1, administrados por Boot. No se añade Hibernate ORM/JPA. Configuración Jackson estricta de tipos y campos desconocidos; binding MVC UUID explícito porque el editor heredado puede preceder al ConversionService y aceptar UUID abreviados.

## Respuestas seguras

```json
{"productName":"Fiscuvia","tagline":"Todo tu despacho, una sola vía.","apiVersion":"1.0.0"}
```

Ejemplo sintético de error (el UUID real cambia en cada petición):

```json
{
  "type":"urn:fiscuvia:problem:validation-failed",
  "title":"Datos inválidos",
  "status":422,
  "detail":"Revisa los campos indicados antes de continuar.",
  "instance":"urn:uuid:00000000-0000-4000-8000-000000000001",
  "code":"VALIDATION_FAILED",
  "traceId":"00000000-0000-4000-8000-000000000001",
  "fieldErrors":[{"field":"displayName","code":"REQUIRED","message":"Este campo es obligatorio."}]
}
```

ApiExceptionHandler sanitiza excepciones MVC/validación/errores explícitos; SafeErrorController cubre dispatch servlet. El fallback inesperado registra sólo código/UUID, sin throwable, mensaje, SQL, request, URL ni headers. No se reflejan rejectedValue, secretos, rutas o datos personales. Los estados imprevistos se convierten en error interno seguro. Errores del conector HTTP anteriores al dispatch pueden no tener cuerpo; no se promete controlar una capa de red/gateway futura.

No hay rate limiter ni persistencia de idempotencia/concurrencia. Preparar códigos 401/403/429 y syntax de claves no acredita seguridad por despacho. #26–30 deberán integrar policies y filtros de seguridad con este formato.

## Contratos y cliente

OpenAPI 3.1 implementado separado del borrador propuesto, validación Redocly y generación TS comprobada. AJV 2020-12 verifica respuestas HTTP reales capturadas por JUnit, todos los códigos/status/correlación y campos extra. Backend CI añade esa verificación después de clean verify; no cambia triggers, permisos, presupuesto ni política de workflows. Las respuestas crudas permanecen en target, fuera de artifacts.

Cliente headless en web/src/shared/api: tipos/metadatos generados desde el contrato, URL pública configurable, validación runtime del DTO, errores normalizados sólo a code/status/traceId. No conserva detail ni fieldErrors externos arbitrarios, no registra cuerpos, no reintenta, no sigue redirects y usa credentials: omit. Preserva AbortSignal. No hay fetch al montar UI ni cambios visuales.

`VITE_API_BASE_URL` default /api/v1; nunca secretos VITE_. Vite dev proxy /api → http://127.0.0.1:8080; preview/build requieren mismo origen con gateway o configuración CORS aprobada en una entrega futura. No se habilita CORS global, staging ni producción. Las URLs con credenciales/query/fragment o protocolo distinto de HTTP(S) se rechazan.

## Pruebas y límites

- Se conserva toda la cobertura anterior; 80 Surefire + 10 Failsafe = **90** ejecuciones backend. 39 nuevas HTTP y 3 de utilidades; se amplían pruebas existentes de superficie/JAR.
- DTOs requeridos, tipos, longitudes/formato, versión, UUID abreviado, JSON malformado, límites y whitelist de paginación; todos los códigos, media types, Allow/challenge, correlación y canarios sintéticos sin fuga en respuesta/log.
- /__contract-test/** sólo existe en tests, excluido de component scanning ordinario; el JAR real verifica su ausencia y sirve info pública.
- 21 Vitest (8 cliente + 13 UI anteriores), 13 Playwright; sin cambios de snapshots/baselines #56. No corresponde evidencia visual nueva porque no cambia la interfaz.
- Herramientas CI: 7 pruebas, schemas/ref/generación y evidencias HTTP; Gitleaks, actionlint/zizmor y Trivy siguen activos. Los resultados definitivos por SHA están en el PR.

Validación local completa según [CI.md](../delivery/CI.md), más `node tools/ci/contracts.mjs --responses backend/target/contract-responses.json`. En Windows usar mvnw.cmd; Node 24.13.1/npm 11.8.0 y JDK 25, Docker Linux/Ryuk obligatorios. No necesita Compose ni .env local.

Entorno comprobado: Windows 11 amd64, Oracle JDK 25.0.3+9-LTS-195, Maven Wrapper 3.9.16, Docker Engine 29.2.0 (Linux), Node 24.13.1 y npm 11.8.0. JUnit 6.0.3, Modulith 2.1.2, PostgreSQL 18.6, Flyway 12.4.0 y Testcontainers 2.0.5. El SHA final probado, comandos/resultados y escaneos se registran en el PR; no se considera el SHA base evidencia de un worktree modificado.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por presupuesto/facturación; seguimiento #79.** Presupuesto $0 por decisión del propietario; no se cambia ni se reintenta/evade la restricción. No deshabilitar workflows ni considerar fallos pre-runner como fallos de código o éxito de CI.

Referencias técnicas: [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html), [Problem Details Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html), [validación Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html). De TrailerSaaS se adoptan separación DTO, comandos/versiones, códigos y correlación; no Firebase, dominios logísticos, sus límites de listas ni casts genéricos sin validación.
