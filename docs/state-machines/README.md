# Máquinas de estados Fiscuvia — Proposed v0.1

Documentar comandos permitidos, roles, invariantes, evidencia y eventos **antes de exponer APIs que mutan estados**. Requieren aprobación contable para reglas específicas; no marcar Accepted porque las diseñó el asistente.

- [Obligación por período](obligation.md)
- [Revisión y presentación](review-and-filing.md)
- [Pago de impuestos del contribuyente](tax-payment.md)
- [Tareas](task.md)
- [Documentos](document.md)
- [Honorarios y cobranza](fees.md)
- [Suscripción](subscription.md)

## Regla de ortogonalidad
`work_state` (preparación/revisión) **no es igual** a `filing_state` (presentación) **ni** `payment_state` (pago). `overdue` se deriva del vencimiento y de políticas, no sustituye al estado.

## Patrón
`Command -> identidad/tenant/capacidad -> comprobar versión/estado/invariantes -> transición -> persistir -> evento/auditoría -> side effects(outbox)`. No permitir `PATCH {status: "paid"}` arbitrario.

## Consideraciones abiertas
OQ-002/003/004/005: exenciones, declaracion complementaria, impuestos sin saldo, revisión unipersonal, pruebas de acuse. Se resuelven con contador antes de migraciones que fijen el modelo definitivo.
