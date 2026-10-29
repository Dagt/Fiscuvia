# Auditoría de referencia: metodología documental de Dagt/TrailerSaas

**Fecha:** 2026-10-29. **Fuente:** inspección directa vía GitHub del repositorio privado `Dagt/TrailerSaas` en su rama `main`, de los archivos y issues mencionados abajo. Esto documenta el **proceso** replicado, no transfiere reglas de negocio de logística al sector fiscal.

## Evidencia observada en TrailerSaaS
| Elemento real | Referencia verificada en TrailerSaaS | Adaptación en Fiscuvia |
|---|---|---|
| Índice de conocimiento | [docs/README.md](https://github.com/Dagt/TrailerSaas/blob/main/docs/README.md) | [docs/README.md](../README.md) |
| Investigación y entrevistas | `docs/discovery/design-partner-baseline.md`, `questionnaire-answer-matrix.md`, `follow-up-questionnaire-2026-10-03.md`, `final-questionnaire-2026-10-04.md` | `docs/discovery/accounting-firm-workflow.md`, `evidence-matrix.md`, `research-plan.md` |
| Requisitos y alcance | `docs/product/requirements-catalog.md` FR-###; `business-rules.md` BR-###; `mvp-scope.md` | [FR](../product/requirements-catalog.md), [BR](../product/business-rules-catalog.md), [MVP](../product/MVP.md) |
| NFR y cuestiones abiertas | `docs/architecture/non-functional-requirements.md` NFR-###; `docs/product/open-questions.md` OQ-### | [NFR](../architecture/non-functional-requirements.md), [OQ](../product/open-questions.md) |
| Dominio y modelo relacional | `docs/domain/domain-model.md`; `docs/data-model/schema-v1.0.md` | [domain](../domain/domain-model.md), [schema v0.1](../data-model/schema-v0.1.md) |
| Máquinas de estados | `docs/state-machines/README.md` y archivos por entidad | [Máquinas Fiscuvia](../state-machines/README.md) |
| UX por actor | `docs/ux/tenant-admin-web.md`, `transport-operations-web.md`, `customer-dispatcher-web.md`, etc. | [Admin](../ux/firm-admin-web.md), [contador](../ux/accountant-operations-web.md), [portal](../ux/taxpayer-portal-web.md) |
| Contrato API y OpenAPI | `docs/api/conventions.md`, `endpoint-catalog.md`, `openapi-v1-draft.yaml` | [API](../api/README.md) |
| Arquitectura aceptada | `docs/decisions/ADR-001` … `ADR-022` y ADR-024 | [15 ADR propuestos](../decisions/README.md), pendiente aprobación |
| Issues por épica | [Épica #1](https://github.com/Dagt/TrailerSaas/issues/1), [#15 scaffold](https://github.com/Dagt/TrailerSaas/issues/15), [#26 RLS](https://github.com/Dagt/TrailerSaas/issues/26) | Épicas Fiscuvia #2–17, hijos #18–72 con códigos y dependencias |
| Tracker de MVP | [#66 TrailerSaaS](https://github.com/Dagt/TrailerSaas/issues/66) | [#73 Fiscuvia](https://github.com/Dagt/Fiscuvia/issues/73) |
| Olas de implementación | `docs/backlog/implementation-order.md` | [oleadas Fiscuvia](../backlog/implementation-order.md) |
| Gate relacional antes de migrar | [#74 TrailerSaaS](https://github.com/Dagt/TrailerSaas/issues/74), schema hardening | [#74 Fiscuvia](https://github.com/Dagt/Fiscuvia/issues/74), revisión fiscal/esquema |
| Normas Codex y PR | `AGENTS.md`, `docs/backlog/codex-workflow.md` | [AGENTS](../../AGENTS.md), [Codex workflow](../backlog/codex-workflow.md) |
| Rediseño visual tardío | `docs/ux/design-system.md`, `frontend-redesign-master-plan.md`, `visual-acceptance-criteria.md`, [UX-04 #121](https://github.com/Dagt/TrailerSaas/issues/121) | [plan UX desde inicio](../ux/frontend-foundation-plan.md) + criterios visuales bloqueantes |

## Diferencias intencionales, no copiar literalmente
- TrailerSaaS es logística de trailers, con Flutter/Drift offline, QR, geocercas y workflows de viajes. Fiscuvia NO requiere ese ámbito en MVP.
- La dirección visual final 'Fleet Simple' de TrailerSaaS usa sidebar clara/azul; **Fiscuvia usa su propia dirección de arte navy/teal**, aprobada por usuario en el mockup de seis pantallas.
- TrailerSaaS marcó diversas decisiones como **Accepted** en la fase inicial. Los nuevos documentos Fiscuvia aún no tienen aprobación explícita: permanecen **Proposed**. No simular aceptación.
- Los ejemplos del despacho contable están basados en audio, no en observación directa validada como ocurrió en parte del diseño de TrailerSaaS. Hay que completar discovery y entrevistar usuarios.
- Integramos seguridad de e.firma/SAT, CFDI y regulación mexicana como riesgos del dominio. No transferir permisos ni políticas de logística.

## Gaps reales todavía abiertos
1. Obtener y añadir al repo el PNG conceptual de seis pantallas aprobado; se documentó la dirección, pero no hay archivo binario en GitHub.
2. Verificar y resolver OQ-001..006 con un contador antes del esquema definitivo de obligaciones.
3. Validar versiones Java/Spring Boot/Modulith/OIDC y contratos fiscales; la propuesta técnica no sustituye prueba.
4. Revisar/aprobar formalmente las decisiones ADR, PRD, esquemas, API y prioridades de MVP.
5. Crear el código de aplicación y sus pruebas mediante issues; **al momento de la auditoría todavía no existe implementación**.
6. Completar contrato OpenAPI: el archivo actual es un esquema parcial, no tiene los contratos por endpoint exhaustivos que TrailerSaaS desarrolló antes de implementar. Debe expandirse mediante issues de diseño/implementación.

## Política de mantenimiento
Mantener requisitos/estados/schema/API y el backlog alineados tras cada issue/PR, pero no editar silenciosamente documentos Accepted. Si algo cambia: issue de enmienda, nueva propuesta, review y registro explícito de decisión.
