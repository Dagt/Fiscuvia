# Validación C01 #34

Base main verificada después de #92: `4c748a833a1ca2f0deef5c6fbd7702c0f66996cd`. Rama `issue-34-taxpayer-domain-api`. [Implementación, alcance y riesgos](../architecture/TAXPAYER-DOMAIN-34.md). Evidencia del SHA final y CI remoto en el PR; un resultado anterior no acredita una revisión nueva. Presupuesto GitHub $0 intacto.

## Comandos reproducibles

Desde raíz, Java25, Node24.13.1/npm11.8.0 y Docker Linux:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -B -ntp clean verify
npm ci --prefix tools/ci --ignore-scripts
npm ci --prefix web
npm run openapi --prefix tools/ci
node tools/ci/contracts.mjs --responses backend/target/contract-responses.json --taxpayer-responses backend/target/taxpayer-contract-responses.json
node tools/ci/reports.mjs backend
node tools/ci/artifacts.mjs backend
npm run typecheck --prefix web
npm run lint --prefix web
npm run format:check --prefix web
npm test --prefix web -- --reporter=default --reporter=json --outputFile=../.ci-work/vitest.json
npm run build --prefix web
```

Navegador, una vez terminado Maven (comparte target, no ejecutar simultáneamente con clean):

```powershell
$env:PLAYWRIGHT_JSON_OUTPUT_FILE='../.ci-work/playwright.json'
npm run test:e2e --prefix web -- --reporter=line,json
node tools/ci/auth-browser.mjs
node tools/ci/reports.mjs web
node tools/ci/artifacts.mjs web
npm test --prefix tools/ci
.\backend\mvnw.cmd -f backend/pom.xml -B -ntp org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom -DincludeTestScope=true -DoutputFormat=json
npm run security --prefix tools/ci
```

GitHub conserva Backend CI, Web CI, OpenAPI CI, Security CI y CI Required. Cada artefacto público se limita a resumen/identificadores de prueba; cuerpos HTTP sintéticos, logs, sesiones y material temporal permanecen privados. El JAR se inspecciona para excluir fixtures, harness OIDC y claves de prueba.

## Oráculos relevantes

- TaxpayerValidationTests: formato/estructura de personas, fechas, normalización y exclusión exacta de genéricos; nunca existencia SAT.
- TaxpayerTests: HTTP con Keycloak real, CSRF, PostgreSQL runtime, roles y MFA; cuotas reales, perfil/historia, archivado/restauración, contratos y metadatos seguros.
- Revisión efectiva: ausencia de grant, grants con rol inválido, latest DISABLE, suspensión/reactivación y ciclo de roles mediante API #33; replay denegado tras pérdida de habilitación; historia preservada.
- RLS: consulta sin filtro, GUC forjado, recurso ajeno y pool de una conexión; runtime/sesiones sin acceso privado o DML; FK compuestas e historia inmutable.
- Concurrencia: RFC duplicado, última plaza, expectedVersion, edit/archive, restore, misma clave y locks reales frente a cambios de rol/revocación.
- Rollback inducido en journal, outbox, revisiones, regímenes, PRIMARY, grant_reviews y recibo; reintento posterior válido sin filas/eventos residuales.
- Drift deliberado de RLS/FORCE, append-only, privilegios y MFA/matriz; guard de arranque rechaza cada mutación y se restaura la protección.
- TaxpayerMigrationIT: upgrade V11/checksums, instalación/ejecución única, modificación de checksum y fallo DDL con rollback/reintento.
- Regresiones #28/#30: usan contribuyentes sintéticos respaldados por filas y revisiones válidas. No se debilita FK ni revisión fiscal para conservar fixtures huérfanos. El oráculo de membresía comprueba ahora también la protección independiente de revisión/versionado V12.

Las suites y mínimos existentes se conservan. Nuevos mínimos en reports.mjs: TaxpayerTests57, TaxpayerValidationTests5 y TaxpayerMigrationIT4; base456 preservada, total esperado522 Java (474 Surefire +48 Failsafe),132 Vitest,61 Playwright,20 OIDC y26 tools/ci. Este documento no afirma validación de configuración productiva, volumen sostenido, reglas fiscales, catálogos SAT ni módulos futuros. El PR registra los resultados efectivos y enlaces de CI del SHA publicado; los mínimos anteriores son exigencias del gate, no una afirmación de ejecución por sí mismos.
