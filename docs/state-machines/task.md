# Task state machine (Proposed)

`OPEN` -> `IN_PROGRESS` -> `IN_REVIEW` -> `DONE`. `IN_REVIEW` -> `IN_PROGRESS` al rechazar; `OPEN|IN_PROGRESS` -> `CANCELLED` autorizado. `REOPEN` crea evento motivo; fecha vencida es condición derivada, no estado.

Campos: tenant, taxpayer opcional, obligation opcional, assignee, due_date, priority, checklist, source, version. No pérdida de historial por reasignación o cierre.

Comandos: AssignTask, StartTask, SubmitForReview, ApproveTask, RejectTask, CompleteTask, CancelTask, ReopenTask. Revisiones pueden omitirse con política específica; no lo suponer para todos.

Auditoría actor/time y permisos según cartera. Generación recurrente idempotente. Tests de transición, roles, concurrencia, fecha y aislamiento.
