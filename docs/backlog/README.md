# Backlog de implementación Fiscuvia

**Estado:** Proposed · **Tracking issue:** [#73 — Fiscuvia MVP](https://github.com/Dagt/Fiscuvia/issues/73) · **Código:** backend #20 integrado mediante PR #75; scaffold web #21 en esta rama, pendiente de revisión/merge, sin módulos de negocio. #52 y #56 conservan sus alcances independientes. El backlog incorpora metodología real de TrailerSaaS: épica (resultado) -> issue acotado (PR) -> criterios de aceptación -> pruebas -> revisión humana -> merge.

## Épicas y tareas
| Épica | Issues hijos | Resultado | Gate |
|---|---|---|---|
| [#2 E00 Fundamentos producto/UX](https://github.com/Dagt/Fiscuvia/issues/2) | #18–19 | requisitos y estilo revisados | G0 |
| [#3 E01 Plataforma](https://github.com/Dagt/Fiscuvia/issues/3) | #20–25 | scaffold, DB, CI, APIs y observabilidad | G1 |
| [#4 E02 Identidad/RLS](https://github.com/Dagt/Fiscuvia/issues/4) | #26–30 | aislamiento y autorización | G1 |
| [#5 E03 SaaS](https://github.com/Dagt/Fiscuvia/issues/5) | #31–33 | despachos, miembros, cuotas | G1 |
| [#6 E04 Contribuyentes](https://github.com/Dagt/Fiscuvia/issues/6) | #34–37 | directorio/expediente | G2 |
| [#7 E05 Obligaciones](https://github.com/Dagt/Fiscuvia/issues/7) | #38–42 | plantilla, período, alertas, calendario | G2–3 |
| [#8 E06 Tareas/workflow](https://github.com/Dagt/Fiscuvia/issues/8) | #43–45 | preparación/revisión/presentación/pago | G3 |
| [#9 E07 Documentos](https://github.com/Dagt/Fiscuvia/issues/9) | #46–48 | archivos privados/evidencias | G3 |
| [#10 E08 Honorarios](https://github.com/Dagt/Fiscuvia/issues/10) | #49–51 | cuotas, cobranza y aging | G3 |
| [#11 E09 Dashboard/UX](https://github.com/Dagt/Fiscuvia/issues/11) | #52–56 | UI consistente, reportes, búsqueda | G4 |
| [#12 E10 Portal](https://github.com/Dagt/Fiscuvia/issues/12) | #57–59 | colaboración contribuyente | Future |
| [#13 E11 Billing](https://github.com/Dagt/Fiscuvia/issues/13) | #60–61 | suscripciones y cobro automático | Future |
| [#14 E12 CFDI](https://github.com/Dagt/Fiscuvia/issues/14) | #62–64 | XML, indexación, centro CFDI | Future |
| [#15 E13 SAT](https://github.com/Dagt/Fiscuvia/issues/15) | #65–67 | conectividad oficial y jobs | Future con ADR |
| [#16 E14 Bancos](https://github.com/Dagt/Fiscuvia/issues/16) | #68–69 | conciliación asistida | Future |
| [#17 E15 Operación](https://github.com/Dagt/Fiscuvia/issues/17) | #70–72 | backup/staging/UAT y lanzamiento | G5–6 |

## Gate documental adicional

- [#74 — Revisar esquema fiscal v0.1 antes de las primeras migraciones definitivas de obligaciones](https://github.com/Dagt/Fiscuvia/issues/74). El gate no bloquea scaffolds, pero sí la fijación de estados/datos fiscales sin aprobación de OQ-001..006.

## Contratos de ejecución
- [Orden y oleadas](implementation-order.md)
- [Flujo Codex](codex-workflow.md)
- [Matriz de trazabilidad](requirements-traceability.md)
- [Definición de terminado](../delivery/DEFINITION-OF-DONE.md)
- [Catálogo FR](../product/requirements-catalog.md), [BR](../product/business-rules-catalog.md), [NFR](../architecture/non-functional-requirements.md), [OQ](../product/open-questions.md)
- [ADR](../decisions/README.md), [estado de la UI](../ux/README.md), [datos](../data-model/schema-v0.1.md), [API](../api/README.md)

## Normas
1. No implementar épica completa en PR; una issue acotada por rama.
2. Documentación propuesta no es baseline Accepted hasta aprobación explícita del propietario.
3. Nunca extender alcance sin issue/OQ y actualización FR/BR/ADR.
4. No introducir llaves SAT ni datos reales durante scaffolding.
5. Desarrollo de UI exige pantallas reales con screenshots, no sólo estructura funcional.
6. Dependencias y gates prevalecen sobre número de issue si se adelanta trabajo.
