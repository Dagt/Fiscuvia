# Fiscuvia
**Todo tu despacho, una sola vía.**

Plataforma SaaS B2B para la operación de despachos contables en México. Centraliza contribuyentes, obligaciones, expedientes, colaboración, honorarios, alertas y reportes; posteriormente incorporará CFDI, conectividad SAT y conciliación asistida.

> **Estado**: especificación y planificación. No hay producto ejecutable ni integración SAT implementada. No utilizar datos fiscales reales hasta superar las puertas de seguridad y cumplimiento.

## Leer antes de desarrollar
1. [AGENTS.md](AGENTS.md): contrato operativo para Codex; PRs sin merge autónomo.
2. [Visión y posicionamiento](docs/product/VISION.md), [PRD](docs/product/PRD.md), [alcance del MVP](docs/product/MVP.md).
3. [Arquitectura](docs/architecture/SYSTEM.md), [modelo de datos](docs/architecture/DATA-MODEL.md) y [seguridad](docs/security/SECURITY-BASELINE.md).
4. [Sistema de diseño](docs/ux/DESIGN-SYSTEM.md), [pantallas](docs/ux/SCREENS.md), [criterios visuales](docs/ux/VISUAL-ACCEPTANCE.md).
5. [Épicas y dependencias](docs/delivery/EPICS.md), [roadmap](docs/delivery/ROADMAP.md), [Definition of Done](docs/delivery/DEFINITION-OF-DONE.md).
6. [Política fiscal y de integraciones](docs/integrations/SAT-CFDI.md).
7. [Proceso de contribución](CONTRIBUTING.md) y [gestión de riesgos](docs/delivery/RISKS.md).

## Decisiones vigentes
- Java 25 LTS (previa validación de compatibilidad; Java 21 LTS como alternativa), Spring Boot estable compatible, monolito modular con Spring Modulith.
- React + TypeScript + Vite, Tailwind, shadcn/ui, TanStack Query/Table, RHF y Zod.
- PostgreSQL con aislamiento multitenant y RLS; almacenamiento de archivos privado tipo S3/R2.
- Arquitectura API-first, pruebas automatizadas y trazabilidad; sin microservicios prematuros.
- MVP: gestión y control administrativo; **no** motor de cálculo de impuestos, contabilidad general, scraping SAT ni custodia de e.firma.
- Cada cambio mediante issue -> rama -> PR draft/revisión -> aprobación humana -> merge humano.

## Convenciones
- Texto de producto/UX: español de México. Identificadores técnicos/código: inglés consistente.
- No almacenar contraseñas, .key/.cer, tokens, RFC reales, XML reales, datos personales ni secretos en Git.
- La imagen conceptual aprobada en conversación es una referencia de dirección de arte; las pantallas implementadas deben demostrarse mediante capturas de la app real.

Marca y dominio: sujeto a validación legal/registral; el nombre seleccionado para trabajar es Fiscuvia.
