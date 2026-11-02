# Pruebas negativas y mutaciones — #30

Las mutaciones son ensayos de resistencia **sólo en PostgreSQL desechable de Testcontainers**, nunca comandos de desarrollo/producción. No editar V1–V5, desactivar CI ni usar migrador/superusuario como lector runtime. El bootstrap de pruebas sólo modifica DDL cuando requiere CREATEROLE; la observación de acceso sigue siendo HTTP productivo o SQL runtime.

Desde backend, POSIX `./mvnw -B -ntp -Dtest=CrossTenantRegressionTests test`; PowerShell `./mvnw.cmd -B -ntp '-Dtest=CrossTenantRegressionTests' test`. Es diagnóstico focal, **no sustituye** `clean verify`, OIDC navegador, seguridad ni reporte completo. No pasar skipTests, disabledWithoutDocker, reuse o Ryuk disabled. Keycloak/PG Linux, puertos efímeros y JDK 25 requeridos; no hay servicios externos ni secretos GitHub.

| Mutación controlada | Oráculo | Restauración |
|---|---|---|
| Quitar resource_fence del fixture | HTTP list/count empieza a mezclar carteras y falla la aserción | Recrear USING/WITH CHECK capturados en finally; mismo oráculo vuelve a pasar |
| Relajar tenant_fence o NO FORCE RLS en app.tenants | Guarda productiva verifyRuntimeDatabase rechaza catálogo | FORCE/predicado originales y guarda vuelve a pasar |
| BYPASSRLS runtime | Guarda rechaza rol y oráculo HTTP detecta filas extra | NOBYPASSRLS en finally; consulta runtime nuevamente restringida |
| GRANT SELECT/UPDATE de journal o EXECUTE claim a runtime | Guarda productiva verifyJournal rechaza grants | REVOKE específico y guarda pasa |
| SECURITY DEFINER con search_path public | Catálogo exige owner migrador y pg_catalog,pg_temp | pg_get_functiondef original restaurado; catálogo pasa |
| Omitir ACTIVE en las dos funciones de autorización | HTTP de usuario suspendido cambia indebidamente 404→200; oráculo falla | Ambas definiciones exactas restauradas; 404 comprobado |
| Relajar MFA de política sintética de exportación | Sesión real sin mfaAt obtiene 200 en vez de 403; oráculo falla | requires_mfa y principal originales, 403 comprobado antes de restaurar sesión |
| Quitar trigger append-only/cerca journal | AuditJournalTests detecta catálogo/filas | finally recrea y vuelve a comprobar |
| Reducir suite o retirar método crítico/reclasificarlo futuro | Tests tools/ci y verificador de JUnit rechazan reporte | Fixture de reporte en memoria; no alterar workflow |

No toda retirada produce fuga mientras otra capa sigue funcionando: tenant_fence y resource_fence son defensa en profundidad. En esos casos el oráculo **de catálogo** prueba drift y se reporta como tal; no se atribuye una fuga de filas que no ocurrió. Restauración en finally y cierre de contenedores/Ryuk aíslan incluso un fallo de aserción; nunca ejecutar esta suite contra una URL arbitraria.

El oráculo se invoca primero con protección, luego se espera AssertionError/guarda rechazada tras la mutación y finalmente se invoca tras restaurar. La suite global queda verde sólo si detectó el debilitamiento; una mutación no detectada provoca fallo. No se interpreta el AssertionError esperado como test omitido. Las 45 pruebas nuevas permanecen normales y obligatorias.

Los escenarios críticos referencian métodos exactos en [matriz ejecutable](../../tools/ci/security-scenarios.mjs). El reporte sanitizado excluye stdout/properties/excepciones y datos runtime. Consultar filas/logs privados sólo localmente y no subirlos. Concurrencia usa locks/latches/barreras con timeout y nunca se acredita sólo por haber invocado un mock.
