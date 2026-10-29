# Fiscuvia

**Todo tu despacho, una sola vía.**

SaaS B2B para la gestión operativa de despachos contables en México: contribuyentes, obligaciones, calendario, responsables, documentos y acuses, honorarios, reportes y control de cumplimiento **registrado por el despacho**. CFDI y conexión directa SAT son fases posteriores, sujetas a viabilidad/seguridad; no reemplaza CONTPAQi Contabilidad ni un PAC.

> **Estado al 2026-10-29:** etapa documental. No existe aplicación funcional ni integración SAT. La documentación es Proposed mientras no sea aceptada mediante revisión explícita. El PR #1 sigue Draft y sin merge.

## Para Codex y desarrolladores
1. [AGENTS.md](AGENTS.md): reglas obligatorias, documentación fuente de verdad y PR Draft sin merge.
2. [Mapa completo de documentación](docs/README.md): descubrimiento, producto, dominio, estados, datos, contratos, ADR, UX y backlog.
3. [Catálogo FR-###](docs/product/requirements-catalog.md), [BR-###](docs/product/business-rules-catalog.md), [NFR-###](docs/architecture/non-functional-requirements.md), [OQ-###](docs/product/open-questions.md).
4. [MVP](docs/product/MVP.md) y [modelo de dominio](docs/domain/domain-model.md).
5. [Decisiones ADR-###](docs/decisions/README.md), [esquema PostgreSQL v0.1](docs/data-model/schema-v0.1.md), [API](docs/api/README.md).
6. [Sistema visual](docs/ux/DESIGN-SYSTEM.md), [pantallas](docs/ux/screen-inventory.md) y [criterios obligatorios](docs/ux/VISUAL-ACCEPTANCE.md).
7. [Backlog](docs/backlog/README.md), [oleadas](docs/backlog/implementation-order.md), [protocolo Codex](docs/backlog/codex-workflow.md), [DoD](docs/delivery/DEFINITION-OF-DONE.md).

## GitHub
- [PR #1 — documentación fundacional](https://github.com/Dagt/Fiscuvia/pull/1) — **Draft**, espera aprobación humana.
- [Issue #73 — seguimiento MVP](https://github.com/Dagt/Fiscuvia/issues/73) — épicas, dependencias y gates.
- [Issue #74 — revisión del modelo fiscal/esquema antes de migraciones](https://github.com/Dagt/Fiscuvia/issues/74).

## Decisiones de dirección vigentes
Nombre Fiscuvia, eslogan y SaaS multi-tenant con Java/Spring Boot + React/TS/PostgreSQL. Versión exacta de frameworks, proveedor OIDC, reglas fiscales y precios permanecen Proposed o Open hasta pruebas/validación.

## Calidad UX
La referencia aprobada es una composición conceptual de seis pantallas, con sidebar navy, acento teal y estilo limpio/premium. Todavía debe incorporarse su imagen fuente al repositorio. Cada PR frontend necesitará screenshots **reales**, accesibilidad y revisión comparativa; no se acepta un frontend genérico.

## Seguridad
Datos sintéticos hasta gates de privacidad/seguridad. No subir llaves e.firma, contraseñas SAT, RFC/CFDI reales, tokens ni información fiscal de clientes. No afirmar certificación SAT ni estados oficiales sin fuente.
