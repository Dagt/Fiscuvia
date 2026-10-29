# Backlog y ejecución con Codex
## Estructura
Épicas son issues de seguimiento (no se implementan como una sola PR); issues hijos son cortes verticales revisables. Tipos: `[Epic E##]`, `[Foundation]`, `[Feature]`, `[UX]`, `[Security]`, `[Spike]`. Prioridades: P0 bloqueante, P1 MVP, P2 fase 3, P3 futuro. Etiquetas de GitHub se podrán añadir cuando se habilite su creación; títulos permanecen autoidentificables.
## Cada issue debe tener
Actor/problema; alcance exacto; exclusiones; prerequisitos; criterios Given/When/Then; seguridad/tenant/roles; contratos/API/DB; UX/estados; plan de pruebas y evidencia real; documentación afectada.
## Orden
Documentación aprobada -> scaffold y CI -> identidad/RLS -> clientes -> obligaciones -> tareas/revisión -> documentos -> honorarios -> dashboard/export -> gates piloto. Portal, CFDI y SAT después, con dependencias verificadas.
## Procedimiento
Codex recibe un issue, crea rama `issue-N-slug`, realiza tests, prepara Draft PR, enumera archivos/resultado, screenshots reales si UI. Usuario revisa; Codex nunca mergea por iniciativa propia. Los epics se completan sólo cuando sus hijos cumplen DoD.
