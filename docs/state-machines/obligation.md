# ObligationOccurrence — máquina compuesta (Proposed)

**Refs:** FR-020..031, BR-020..030, OQ-001..005.

## Entidad
Una ocurrencia fiscal se identifica por tenant + contribuyente + plantilla versión + período. Contiene fecha de vencimiento y estados separados; no es un simple `done=true`.

## Dimensión work_state
`PENDING` -> `IN_PROGRESS` -> `IN_REVIEW` -> `APPROVED`. Rechazo: `IN_REVIEW` -> `IN_PROGRESS` con motivo. `NOT_APPLICABLE` sólo por decisión autorizada con razón y política (OQ pendiente). Reapertura: `APPROVED` -> `IN_PROGRESS` con razón/auditoría, nunca borrar aprobación previa.

## Dimensión filing_state
`NOT_FILED` -> `FILED_RECORDED` al aportar fecha, actor y evidencia según política. Una fuente de verificación oficial futura puede marcar `SAT_VERIFIED` **sólo tras integración oficial y validación real**, no manual. Corrección posterior se representa como nuevo evento/evidencia, no borrado.

## Dimensión payment_state
`NOT_REQUIRED` o `PENDING` -> `PAYMENT_RECORDED`; ver [tax-payment.md](tax-payment.md). 'Pagada' no es sinónimo de 'presentada'. Una obligación puede estar FILED_RECORDED + PENDING.

## Vencimiento
`OVERDUE` y `DUE_SOON` como flags/proyecciones calculadas con fecha y política, no transiciones que destruyen estado.

## Comandos y autorización
| Comando | Precondición | Efectos |
|---|---|---|
| StartWork | PENDING, rol preparado | IN_PROGRESS, audit |
| SubmitReview | IN_PROGRESS, evidencia según política | IN_REVIEW, audit |
| Approve | IN_REVIEW, supervisor/capacidad | APPROVED, audit |
| Reject | IN_REVIEW, supervisor con motivo | IN_PROGRESS, audit |
| RecordFiling | aprobado o excepción autorizada; fecha/acuse | FILED_RECORDED, evidence + audit |
| RecordTaxPayment | filing/policy y comprobante | payment state update + audit |
| Reopen | rol elevado, motivo, expectedVersion | nuevo evento y work IN_PROGRESS |
| MarkNotApplicable | rol autorizado y motivo | NOT_APPLICABLE, audit |

## Pruebas
No se puede marcar pagado por 'filed'; inválidos dan 409/422 seguro, permisos 403, duplicados idempotentes, dos tenants no cruzan, historial permanece, no se muestran estados SAT inventados.
