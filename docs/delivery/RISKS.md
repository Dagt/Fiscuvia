# Registro de riesgos — actualización continua
| Código | Riesgo | Criticidad | Mitigación |
|---|---|---|---|
| R01 | fuga de datos entre despachos | crítica | RLS, autorización, pruebas negativas y auditoría |
| R02 | uso indebido e.firma | crítica | sin custodia en MVP, ADR futuro y revisión independiente |
| R03 | fecha/obligación fiscal equivocada | alta | plantillas versionadas revisadas por contadores |
| R04 | marcar presentada como validada SAT | alta | distinguir registro interno, acuse y verificación oficial |
| R05 | pérdida documental | crítica | backup cifrado, recuperación ensayada |
| R06 | frontend genérico visualmente inferior al diseño | alta | sistema visual, screenshots y rúbrica bloqueante |
| R07 | baja adopción frente a Optimiza | alta | entrevistas, pilotos y propuesta de valor verificada |
| R08 | SAT cambia API o tiene caídas | alta | adaptador, reintentos, límites y pruebas de contrato |
| R09 | costos de almacenamiento/descarga descontrolados | alta | métricas, cuotas y presupuestos por tenant |
| R10 | expansión incontrolada de MVP | alta | alcance excluido y gates por fase |
| R11 | temas legales y privacidad sin resolver | alta | revisión legal antes de uso con datos reales |
| R12 | CI remoto no disponible por facturación | media | evidencia local, no declarar aprobado |
| R13 | branding/dominio no registrados | media | verificación IMPI y dominio antes de marca pública |
| R14 | dependencia de un solo desarrollador | alta | ADR, docs, pruebas y runbooks |
Todos los riesgos críticos abiertos bloquean lanzamiento con información real. Asignar dueño, probabilidad y fecha de revisión en planeación.

## #32 — entitlements técnicos

Pendientes explícitos: aprobación limitada de TENANT_PLAN_VIEW y política de membresías/invitaciones antes de activar el hook preparado; sin esa aprobación no se afirma enforcement productivo de aceptaciones. Reservas duraderas requieren liberación explícita: no existe un worker de expiración ni una promesa de retención/limpieza aprobada. Reducciones pueden impedir confirmar reservas previas, conservando datos. Cuotas de almacenamiento/contribuyentes/jobs permanecen sin medición hasta sus módulos reales; períodos de jobs sin definición no se usan para enforcement. Custodia/atribución administrativa y verificación externa de aprobación heredan el gate de provisioning #31. OQ-007/014/015, ADR-015 y #74 permanecen abiertos. [Diseño y dependencias](../architecture/TENANT-ENTITLEMENTS.md).
