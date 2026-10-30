# Fiscuvia

**Todo tu despacho, una sola vía.**

SaaS B2B para la gestión operativa de despachos contables en México: contribuyentes, obligaciones, calendario, responsables, documentos y acuses, honorarios, reportes y control de cumplimiento **registrado por el despacho**. CFDI y conexión directa SAT son fases posteriores, sujetas a viabilidad/seguridad; no reemplaza CONTPAQi Contabilidad ni un PAC.

> **Estado al 2026-10-30:** base #20–23 integrada (PR #75–78). Esta rama incorpora contratos/errores API #24 para revisión. Sin tablas fiscales, autorización ni integración SAT. Los documentos Proposed conservan ese estado hasta aceptación explícita.

## Web local — issue #21

Node 24.13.1 / npm 11.8.0. Desde `web/`:

```powershell
npm ci
npm run dev
```

Bienvenida y estados demostrativos con identidad Fiscuvia, navegación responsive y áreas futuras deshabilitadas. No requiere backend, variables de entorno ni datos de clientes. Consulta [web/README.md](web/README.md) para arquitectura, versiones y comandos de validación; [capturas y evidencia real](docs/ux/evidence/issue-21/README.md). #52 y #56 conservan sus alcances independientes.

## Backend y PostgreSQL local — issues #20/#22

Requiere JDK 25 y Docker con contenedores Linux. Maven se descarga mediante el wrapper. Desde la raíz, generar credenciales sintéticas locales y arrancar PostgreSQL:

```powershell
pwsh -File infra/New-LocalEnvironment.ps1
docker compose --env-file infra/.env -f infra/compose.yaml up -d --wait
```

POSIX: `sh infra/new-local-env.sh` y el mismo comando Compose. Los generadores no sobrescriben `.env`. Ver [guía PostgreSQL, permisos, variables y limpieza](infra/README.md).

```powershell
cd backend
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd spring-boot:run
```

En POSIX: `./mvnw -B -ntp clean verify` y `./mvnw spring-boot:run`.
Salud: `http://127.0.0.1:8080/actuator/health`. La aplicación escucha en loopback, admite sólo local/test y requiere PostgreSQL. Los tests crean DBs efímeras con Testcontainers, sin necesitar Compose ni `.env`. No hay fallback H2, tablas fiscales, RLS ni endpoints de negocio.
Consulta [backend/README.md](backend/README.md) y [evidencia #22](backend/VALIDATION-22.md). Seguridad por despacho en #26–30; gate #74 antes del esquema fiscal.

## Integración continua — issue #23

El workflow [CI](.github/workflows/ci.yml) valida PRs a main, incluso Draft, push a main y ejecución manual. Checks estables: `Backend CI`, `Web CI`, `OpenAPI CI`, `Security CI` y `CI Required`. No despliega ni requiere secrets. Ver [guía CI, comandos equivalentes, artifacts, diagnóstico y protección recomendada](docs/delivery/CI.md). El resultado debe consultarse por SHA en GitHub Actions; la existencia del YAML no acredita una ejecución exitosa.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por presupuesto/facturación; seguimiento [#79](https://github.com/Dagt/Fiscuvia/issues/79).** Presupuesto $0 por decisión del propietario; validación local completa obligatoria, sin deshabilitar workflows ni reintentar runners.

## Contratos API — issue #24

`GET /api/v1/public/info` devuelve sólo marca/eslogan/versión. Errores RFC 9457 seguros, validación servidor y cliente TypeScript con validación runtime, sin nuevas pantallas ni solicitudes automáticas. [Contrato implementado](docs/api/openapi-v1.yaml) separado del [borrador propuesto](docs/api/openapi-v1-draft.yaml); [guía #24 y comandos](docs/api/foundation-24.md). `/api/v1/me`, OIDC, dominio fiscal e idempotencia durable permanecen futuros.

## Para Codex y desarrolladores
1. [AGENTS.md](AGENTS.md): reglas obligatorias, documentación fuente de verdad y PR Draft sin merge.
2. [Mapa completo de documentación](docs/README.md): descubrimiento, producto, dominio, estados, datos, contratos, ADR, UX y backlog.
3. [Catálogo FR-###](docs/product/requirements-catalog.md), [BR-###](docs/product/business-rules-catalog.md), [NFR-###](docs/architecture/non-functional-requirements.md), [OQ-###](docs/product/open-questions.md).
4. [MVP](docs/product/MVP.md) y [modelo de dominio](docs/domain/domain-model.md).
5. [Decisiones ADR-###](docs/decisions/README.md), [esquema PostgreSQL v0.1](docs/data-model/schema-v0.1.md), [API](docs/api/README.md).
6. [Sistema visual](docs/ux/DESIGN-SYSTEM.md), [pantallas](docs/ux/screen-inventory.md) y [criterios obligatorios](docs/ux/VISUAL-ACCEPTANCE.md).
7. [Backlog](docs/backlog/README.md), [oleadas](docs/backlog/implementation-order.md), [protocolo Codex](docs/backlog/codex-workflow.md), [DoD](docs/delivery/DEFINITION-OF-DONE.md).

## GitHub
- [PR #1 — documentación fundacional](https://github.com/Dagt/Fiscuvia/pull/1) — integrado en `ce561482fda7889b005deaad3d822395b89a82df`.
- [PR #75 — scaffold backend #20](https://github.com/Dagt/Fiscuvia/pull/75) — integrado mediante squash en `1d0590a`.
- [PR #76 — scaffold web #21](https://github.com/Dagt/Fiscuvia/pull/76) — integrado mediante squash en `6ac5dd5d3750b76e50494953c10724b25368bad3`.
- [PR #77 — PostgreSQL/Docker/Flyway #22](https://github.com/Dagt/Fiscuvia/pull/77) — integrado mediante squash en `bf61a9697e606cfd6b709a09ed2997947c8c268f`.
- [PR #78 — CI #23](https://github.com/Dagt/Fiscuvia/pull/78) — integrado mediante squash en `edbc6ddc1a54e17e5cd38dd108af67cc5eb7e1c7`; verificación remota pendiente #79.
- [Issue #73 — seguimiento MVP](https://github.com/Dagt/Fiscuvia/issues/73) — épicas, dependencias y gates.
- [Issue #74 — revisión del modelo fiscal/esquema antes de migraciones](https://github.com/Dagt/Fiscuvia/issues/74).

## Decisiones de dirección vigentes
Nombre Fiscuvia, eslogan y SaaS multi-tenant con Java/Spring Boot + React/TS/PostgreSQL. Las versiones comprobadas del scaffold se documentan en `backend/README.md`; no implican aceptación global de ADR. Proveedor OIDC, reglas fiscales y precios permanecen Proposed o Open hasta validación.

## Calidad UX
La referencia aprobada es una composición conceptual de seis pantallas, con sidebar navy, acento teal y estilo limpio/premium. Todavía debe incorporarse su imagen fuente al repositorio. Cada PR frontend necesitará screenshots **reales**, accesibilidad y revisión comparativa; no se acepta un frontend genérico.

## Seguridad
Datos sintéticos hasta gates de privacidad/seguridad. No subir llaves e.firma, contraseñas SAT, RFC/CFDI reales, tokens ni información fiscal de clientes. No afirmar certificación SAT ni estados oficiales sin fuente.
