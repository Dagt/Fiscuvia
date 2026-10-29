# Prompt maestro reusable para Codex
Copia y adapta para el issue concreto. No iniciar desarrollo sin número y PR de documentación aprobado.
```text
Repositorio: Dagt/Fiscuvia. Lee primero README.md, AGENTS.md, docs/product/PRD.md, docs/product/MVP.md, docs/architecture/SYSTEM.md, docs/architecture/TENANCY.md, docs/security/SECURITY-BASELINE.md, docs/ux/DESIGN-SYSTEM.md, docs/ux/VISUAL-ACCEPTANCE.md, docs/delivery/DEFINITION-OF-DONE.md y el issue asignado.

Tarea: implementar EXCLUSIVAMENTE el issue #<N>. Confirma dependencias y main actual; si no se cumplen, detente y reporta bloqueo.
Crea rama issue-<N>-<slug>. Usa monolito modular Java/Spring Boot, PostgreSQL con RLS, React/TS y el sistema visual Fiscuvia. No inventes reglas SAT ni almacenes secretos.
Define primero plan corto y pruebas. Implementa vertical slice y evidencia reproducible; pruebas tenant negativas; API/OpenAPI y docs actualizadas. En frontend entrega screenshots reales 1440x900 y 390x844 + comparación con dirección visual.
Ejecuta checks locales; reporta los resultados y estado real de GitHub CI. No deshabilites tests, no despliegues producción, NO hagas merge ni cierres issues manualmente.
Abre PR Draft, incluye Refs #<N>, resumen, archivos, capturas, comandos, riesgos y pendientes. Espera revisión humana.
```
