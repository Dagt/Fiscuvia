# ADR-015 — Subscription and tenant entitlements

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** FR-093..095, BR-080..082  
**Tracking:** E03/E11

## Contexto y problema
Vender Fiscuvia SaaS a muchos despachos requiere planes y facturación sin exponer datos.

## Requisitos / restricciones
FR-093..095, BR-080..082. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Eliminar datos tras impago; comprobar límites sólo frontend.

## Decisión propuesta
Tenant provisioning, cuotas separadas por usuarios/RFC/storage/jobs, estado de suscripción no destructivo; cobro automatizado posterior.

## Razonamiento y ventajas
Control de costo y escalabilidad comercial.

## Costos / consecuencias negativas
Políticas de cancelación, facturación propia y precios requieren revisión comercial/legal.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E03/E11. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
