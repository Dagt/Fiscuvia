# Fiscuvia — mapa de documentación

**Estado:** propuesta v0.2; PR #1 integrado, sin aceptación automática de cada documento Proposed. Este directorio es la base documental para Codex. Estructura y jerarquía inspiradas en el repositorio real Dagt/TrailerSaas; se han adaptado los conceptos al dominio de despachos contables en México.

## 1. Descubrimiento y evidencia
- [Flujo del despacho entrevistado](discovery/accounting-firm-workflow.md): OneFacture/CONTPAQi/control administrativo según explicación facilitada; observación no validada independientemente.
- [Matriz de hechos, inferencias y preguntas](discovery/evidence-matrix.md)
- [Guion de entrevistas y validación](discovery/research-plan.md)

## 2. Producto y negocio
- [Visión](product/VISION.md), [PRD](product/PRD.md), [MVP](product/MVP.md)
- [Catálogo de requisitos FR-###](product/requirements-catalog.md)
- [Actores, permisos y alcance por contribuyente](product/actors-and-permissions.md)
- [Reglas BR-###](product/business-rules-catalog.md) y [preguntas OQ-###](product/open-questions.md)
- [Personas y journeys](product/PERSONAS-JOURNEYS.md), [glosario](product/GLOSSARY.md)
- [Modelo comercial hipotético](business/COMMERCIAL-MODEL.md), [checklist legal](business/COMPLIANCE-CHECKLIST.md)

## 3. Dominio
- [Modelo de dominio](domain/domain-model.md)
- [Flujos principales](workflows/core-workflows.md)
- [Máquinas de estados](state-machines/README.md): obligación, revisión/presentación, pago fiscal, tareas, documentos, honorarios y suscripciones.

## 4. Datos
- [Esquema relacional propuesto v0.1](data-model/schema-v0.1.md)
- [Modelo conceptual anterior](architecture/DATA-MODEL.md) para contexto; DDL se guía por esquema v0.1 **tras aprobación**.

## 5. API e integraciones
- [Índice de contratos](api/README.md)
- [Convenciones HTTP](api/conventions.md)
- [Catálogo de endpoints](api/endpoint-catalog.md)
- [OpenAPI mínimo propuesto](api/openapi-v1-draft.yaml), explícitamente INCOMPLETO.
- [Conector CFDI/SAT y fases](integrations/SAT-CFDI.md)

## 6. UX y diseño
- [Sistema visual](ux/DESIGN-SYSTEM.md), [inventario de pantallas](ux/screen-inventory.md)
- [Flujos por rol](ux/README.md), [mapa funcional de pantallas](ux/SCREENS.md)
- [Criterios visuales y evidencia real](ux/VISUAL-ACCEPTANCE.md), [concepto aprobado](ux/APPROVED-CONCEPT.md), [flujos](ux/UX-FLOWS.md)

## 7. Arquitectura y seguridad
- [Sistema](architecture/SYSTEM.md), [API](architecture/API.md), [tenancy](architecture/TENANCY.md)
- [NFR-###](architecture/non-functional-requirements.md)
- [Seguridad](security/SECURITY-BASELINE.md), [amenazas](security/THREAT-MODEL.md), [capacidades](security/permission-capabilities.md)
- [ADR index](decisions/README.md), [plantilla ADR](decisions/ADR-000-template.md), y los ADR existentes en `architecture/adr/`.

## 8. Desarrollo y entregas
- [Backlog](backlog/README.md), [oleadas/dependencias](backlog/implementation-order.md), [protocolo Codex](backlog/codex-workflow.md)
- [Índice de issues](delivery/ISSUE-INDEX.md), [Épicas](delivery/EPICS.md), [roadmap](delivery/ROADMAP.md)
- [Definition of Done](delivery/DEFINITION-OF-DONE.md), [tests](delivery/TEST-STRATEGY.md), [gates](delivery/RELEASE-GATES.md), [riesgos](delivery/RISKS.md)
- [Operaciones](delivery/OPERATIONS.md), [prompt Codex](delivery/CODEX-STARTER.md)
- [CI de GitHub Actions, comandos locales y protección de main](delivery/CI.md)
- [Evidencia CI #23 y bloqueo remoto comprobado](delivery/VALIDATION-23.md)

## 9. Referencia comparativa
- [Auditoría de metodología observada en TrailerSaaS y adaptación a Fiscuvia](delivery/TRAILERSAAS-REFERENCE-AUDIT.md)

## Estados documentales
- **Confirmed**: expresamente proporcionado en conversación, audio o documento; indicar fuente y alcance de confirmación.
- **Proposed**: hipótesis de producto o recomendación de arquitectura pendiente de aprobación.
- **Accepted**: aprobado de forma explícita por propietario, no sólo porque exista un archivo o PR.
- **Future**: excluido del MVP, previsto en roadmap.
- **Open**: pregunta sin resolver con OQ y decisión requerida.

## Precedencia (una vez aprobados)
Accepted ADR > requisito/regla aprobada > estado/workflow aprobado > alcance MVP > descubrimiento > supuestos. Un conflicto requiere issue y revisión; Codex no lo resuelve en silencio.

## Estado real
Backend #20, [web #21](../web/README.md) e [infraestructura PostgreSQL/Flyway #22](../infra/README.md) están integrados mediante PR #75–77, con [evidencia #22](../backend/VALIDATION-22.md). Esta rama prepara [CI #23](delivery/CI.md) para revisión. Sin tablas fiscales, negocio ni identidad; #74, #26–30 y #52/#56 siguen pendientes en sus alcances. No cerrar preguntas técnicas o fiscales sin evidencia; no considerar una imagen conceptual captura real.
