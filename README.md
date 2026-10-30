# Fiscuvia

**Todo tu despacho, una sola vía.**

SaaS B2B para la gestión operativa de despachos contables en México: contribuyentes, obligaciones, calendario, responsables, documentos y acuses, honorarios, reportes y control de cumplimiento **registrado por el despacho**. CFDI y conexión directa SAT son fases posteriores, sujetas a viabilidad/seguridad; no reemplaza CONTPAQi Contabilidad ni un PAC.

> **Estado al 2026-10-29:** documentación y backend #20 integrados (PR #75, squash `1d0590a`). Esta rama incorpora el scaffold web #21, pendiente de revisión/merge. No hay funcionalidades de negocio, autenticación ni integración SAT. Los documentos Proposed conservan ese estado hasta aceptación explícita.

## Web local — issue #21

Node 24.13.1 / npm 11.8.0. Desde `web/`:

```powershell
npm ci
npm run dev
```

Bienvenida y estados demostrativos con identidad Fiscuvia, navegación responsive y áreas futuras deshabilitadas. No requiere backend, variables de entorno ni datos de clientes. Consulta [web/README.md](web/README.md) para arquitectura, versiones y comandos de validación; [capturas y evidencia real](docs/ux/evidence/issue-21/README.md). #52 y #56 conservan sus alcances independientes.

## Backend local — issue #20

Requiere JDK 25. Maven se descarga mediante el wrapper; no requiere base de datos, Docker ni proveedor OIDC.

```powershell
cd backend
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd spring-boot:run
```

En POSIX: `./mvnw -B -ntp clean verify` y `./mvnw spring-boot:run`.
Salud: `http://127.0.0.1:8080/actuator/health`. La aplicación escucha en loopback y sólo admite perfiles local/test en este scaffold.
Consulta [backend/README.md](backend/README.md) para versiones, límites modulares, configuración, pruebas y pendientes. La base de datos/Flyway/Docker corresponde a #22; CI a #23; seguridad a #26–30. No hay endpoints de negocio habilitados.

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
- [Issue #73 — seguimiento MVP](https://github.com/Dagt/Fiscuvia/issues/73) — épicas, dependencias y gates.
- [Issue #74 — revisión del modelo fiscal/esquema antes de migraciones](https://github.com/Dagt/Fiscuvia/issues/74).

## Decisiones de dirección vigentes
Nombre Fiscuvia, eslogan y SaaS multi-tenant con Java/Spring Boot + React/TS/PostgreSQL. Las versiones comprobadas del scaffold se documentan en `backend/README.md`; no implican aceptación global de ADR. Proveedor OIDC, reglas fiscales y precios permanecen Proposed o Open hasta validación.

## Calidad UX
La referencia aprobada es una composición conceptual de seis pantallas, con sidebar navy, acento teal y estilo limpio/premium. Todavía debe incorporarse su imagen fuente al repositorio. Cada PR frontend necesitará screenshots **reales**, accesibilidad y revisión comparativa; no se acepta un frontend genérico.

## Seguridad
Datos sintéticos hasta gates de privacidad/seguridad. No subir llaves e.firma, contraseñas SAT, RFC/CFDI reales, tokens ni información fiscal de clientes. No afirmar certificación SAT ni estados oficiales sin fuente.
