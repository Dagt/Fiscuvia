# Review / filing state machine (Proposed)

Estados de revisión: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `SUPERSEDED` para intentos de revisión; no reemplaza work_state de obligación.

| Comando | Desde | Hacia | Actor / restricción |
|---|---|---|---|
| CreateDraftReview | — | DRAFT | usuario con OBLIGATION_PREPARE |
| SubmitReview | DRAFT | SUBMITTED | autoriza y aporta evidence policy |
| Approve | SUBMITTED | APPROVED | OBLIGATION_APPROVE; separación de autoría si política |
| Reject | SUBMITTED | REJECTED | OBLIGATION_REJECT + motivo |
| Revise | REJECTED | nuevo DRAFT | nueva revisión, conserva anterior |
| Supersede | APPROVED | SUPERSEDED + nueva revisión | supervisor con motivo |

La declaración `FILED_RECORDED` es **un registro de presentación creado por usuario** (fecha, referencia, acuse o excepción formal), no estado de review. Una declaración complementaria, si se habilita, crea una presentación/evidencia nueva vinculada al período en vez de modificar el acuse previo.

## Guardias
No autoaprobar cuando existe separación de funciones; no eliminar/reemplazar evidencia sin versión; no afirmar SAT aceptado sin verificación externa. OQ-003/004/005 bloquean política final.

## Tests
Transiciones inválidas 409; author != approver cuando separación activa; permisos; auditoría de rechazo; complementaria no borra historial; tenant y expediente autorizados.
