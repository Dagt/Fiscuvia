# Codex issue workflow — requerido para Fiscuvia

## Antes de comenzar
1. Leer `AGENTS.md`, `docs/README.md`, issue y su dependencia.
2. Verificar que la documentación referenciada esté Accepted; Proposed se usa sólo para preparar PR documental, no inventar comportamiento.
3. Inspeccionar main, pruebas y migraciones; registrar discrepancias de schema/API/estados.
4. Crear rama `issue-N-slug` desde main actual, nunca escribir funcionalidad en docs PR.
5. Proponer lista corta de cambios y pruebas; no ampliar scope.

## Implementación
- Cortes verticales: API/DB/UI/test/documentación necesarios para la misma historia; PR revisable.
- Registrar FR/BR/NFR y ADR en PR; cambios de reglas requerirán review documental.
- Comandos específicos de estado, no PATCH arbitrario; idempotencia, expectedVersion, RLS y auditoría según dominio.
- Si es UI: primero kit de diseño y composición, luego funcionalidad; screenshots reales 1440/390 y otros tamaños según flujo; comparación explícita con concepto.
- No usar fixtures fiscales reales ni credenciales SAT; fake data con procedencia.

## Borrador y evidencia
Abrir **Draft PR**, `Refs #N` (o `Closes #N` sólo si criterios completos y cierre por merge), resumen, APIs, migraciones, tests, estado remoto, capturas, riesgos, checklist. Si CI GitHub falla por billing, declarar `REMOTE CI NOT VERIFIED` y ejecutar checks locales equivalentes. No simular passing.

## Ready y merge
Tras tests y revisión local, marcar Ready **sólo si autorizado** por el owner y evidencias disponibles; GitHub full CI si accesible. Si cambia HEAD, revalidar pruebas afectadas/finales y capturas. **Nunca hacer merge**, cerrar manualmente el issue o desplegar por iniciativa del agente. El owner decide.

## Cierre de épicas
Sólo cuando todos los hijos requeridos están merged y pasan DoD, documentación/schema/API coherente. No declarar MVP completo porque existen PRs Draft.
