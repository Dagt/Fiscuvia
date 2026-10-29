# ADR-003 — PostgreSQL, Flyway and persistence

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** NFR-001,NFR-021  
**Tracking:** E01/E02

## Contexto y problema
Registro fiscal, evidencia, pagos y aislamiento requieren relaciones/consistencia.

## Requisitos / restricciones
NFR-001,NFR-021. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
NoSQL principal; localStorage; DB por tenant desde MVP.

## Decisión propuesta
PostgreSQL administrado, Flyway, JPA para comandos y SQL explícito/jOOQ si se justifica para reportes.

## Razonamiento y ventajas
Transacciones, constraints, RLS, joins y tooling de pruebas.

## Costos / consecuencias negativas
Administrar índices, migraciones y performance; ORM no reemplaza diseño SQL.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E01/E02. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
