# Validación de autorización por recurso #28

Rama `issue-28-resource-authorization-policies`, Refs #28, base main `06707ffcd221c55f6b4e1a6437a82c8571823bee` (#83 integrado). [Diseño/SQL y límites](../docs/security/RESOURCE-AUTHORIZATION.md). El SHA exacto, resultados y tiempos de la ejecución final se publican en el PR Draft y `testedSha` de los summaries depurados. Este archivo versiona el protocolo y mínimos, sin usar un resultado de #27 como evidencia de #28 ni introducir un SHA autorreferencial.

## Entorno

Windows 11 build 26200, Oracle Java 25.0.3+9, Maven Wrapper 3.9.16, Docker Desktop daemon Linux 29.2.0; PostgreSQL 18.6, Flyway 12.4.0, Testcontainers 2.0.5, Boot 4.1.2, Modulith 2.1.2, Security 7.1.2, Session 4.1.1, JUnit 6.0.3; Keycloak 26.8.1 local real. Node 24.13.1/npm 11.8.0, Playwright 1.64.1. No nuevas dependencias. Imágenes/lockfiles/digests conservados; Ryuk activo y reuse deshabilitado. No se afirma ejecución nativa Ubuntu/Temurin mientras Actions no inicie.

## Comandos completos desde raíz

```powershell
npm ci --prefix tools/ci --ignore-scripts
npm ci --prefix web
node tools/ci/identity.mjs
cd backend
./mvnw.cmd -B -ntp clean verify
./mvnw.cmd -B -ntp org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom -DincludeTestScope=true -DoutputFormat=json
cd ../web
npm run typecheck
npm run lint
npm run format:check
npm test -- --reporter=default --reporter=json --outputFile=../.ci-work/vitest.json
npm run build
npx --no-install playwright install chromium
$env:PLAYWRIGHT_JSON_OUTPUT_FILE='../.ci-work/playwright.json'
npm run test:e2e -- --reporter=line,json
cd ..
node tools/ci/auth-browser.mjs
npm run openapi --prefix tools/ci
npm test --prefix tools/ci
node tools/ci/contracts.mjs --responses backend/target/contract-responses.json
node tools/ci/security.mjs
node tools/ci/reports.mjs backend
node tools/ci/reports.mjs web
node tools/ci/artifacts.mjs backend
node tools/ci/artifacts.mjs web
node tools/ci/artifacts.mjs security
docker compose -f infra/compose.yaml --env-file infra/.env config --quiet
git diff --check
```

POSIX: `./mvnw` y `PLAYWRIGHT_JSON_OUTPUT_FILE=../.ci-work/playwright.json npm run test:e2e -- --reporter=line,json`; Ubuntu requiere `playwright install --with-deps chromium` y unzip. No ejecutar Maven limpio y el harness OIDC en paralelo sobre el mismo target. Compose sólo valida el .env sintético ignorado, no lo imprime ni es la DB de tests.

## Gates y pruebas observables

| Comprobación | Mínimo/contenido |
|---|---|
| clean verify | **201**: 179 Surefire + 22 Failsafe; cero fallos/errores/omisiones; conserva 161 previas |
| ResourceAuthorizationTests | **35**: HTTP real/Keycloak/HTTPS y PostgreSQL runtime, capacidades y filas, sin mocks de identidad |
| AuthorizationMigrationIT | **3**: V3→V4 una vez, checksums previos conservados, checksum alterado rechazado, DDL completo rollback/reintento; sólo seis políticas TENANT previamente autorizadas |
| Arquitectura/logging | Modulith previo + rechazo de dependencia de dominio al puerto RLS inferior; decisión JSON cerrada/correlación sin identificadores ni canarios |
| Regresión backend | Todas las suites RLS/pool/OIDC/sesiones/MFA/invitaciones/Flyway/contratos/health/observabilidad/JAR anteriores |
| Web | npm ci/typecheck/lint/format/build; 37 Vitest, 13 Playwright de fundación + 5 OIDC reales, cero omisiones/reintentos/flaky |
| Contratos | OpenAPI implementado y draft, generación TS reproducible, 51 respuestas HTTP anteriores + ocho schemas de identidad en memoria real del navegador |
| tools/ci | Ocho pruebas de gates/contratos, mínimos ampliados sin reducir anteriores |
| Seguridad | Gitleaks historial/worktree, actionlint, zizmor, Trivy npm dev + SBOM Maven test HIGH/CRITICAL; no exclusiones nuevas |
| Artifacts | Sólo summaries/JUnit depurado; escaneo adicional, sin logs crudos/CSRF/tokens/llaves/sesiones/JAR/fixtures |

Las 35 pruebas nuevas abarcan anónimo/expirado/global DISABLED, membresías suspendidas/revocadas, cross-tenant read/edit, carteras distintas same-tenant, lectura sin edición/borrado, edición sin exportación, auxiliar administrativo, supervisor sin scope, owner sin ALL, rol desconocido/no configurado/falsificado y UUID adivinado. Consultas sin filtros de tenant/asignación sólo devuelven filas autorizadas, incluso COUNT/page/export/download/report. Lotes mixtos hacen rollback completo. Estado real/versionado se verifican servidor.

Tres revocaciones concurrentes (asignación/membresía/política) se mantienen no confirmadas mientras la solicitud llega al bloqueo; el test observa `pg_locks` sin conceder pg_monitor y confirma la revocación antes de continuar: operación denegada. El caso inverso demuestra que un permiso ya bloqueado vive hasta commit, la revocación espera y la siguiente operación deniega. No se confunde el inicio de una revocación con su confirmación.

Se prueban sesiones/pool/actor/tenants alternados, contexto sin autorización, SQL directo sin actor, intentos de escribir políticas/grants, MFA ausente/vencido/futuro, retención de scope/cambio anidado, FK compuesta y errores/logs privados. Un owner con MFA y contexto TENANT_READ tampoco puede invocar create_invitation por SQL. Las negativas criptográficas de claims OIDC continúan en la suite real de #27.

El oráculo RLS detecta deliberadamente la eliminación de resource_fence: se observa una AssertionError por filas adicionales, se restaura en finally y vuelve a pasar. No se deshabilitan tests ni FORCE para conseguir éxito. Otras mutaciones comprueban rechazo de la guarda de arranque ante NO FORCE o SELECT runtime sobre grants. Todas las mutaciones están confinadas a DB desechable, con limpieza. JAR excluye controller y SQL fixture; V1/V2/V3 permanecen intactas.

## UI, hallazgos y límites

No hay cambios de UI ni nuevos controles de permisos; se conserva identidad navy/teal y las pantallas reales #27. Playwright OIDC sigue generando capturas reales de acceso desktop/móvil/error como regresión; no son evidencia de un módulo fiscal. No se hacen comparaciones exactas con la imagen conceptual ausente.

El aviso moderado transitivo ESLint/typescript-eslint/minimatch (GHSA-g3hv-75wg-m249), documentado en #27, requiere seguimiento de tooling; no se ejecuta audit fix --force ni se cambia el gate HIGH/CRITICAL. Consultar el resultado actual de Trivy sobre el SHA final, porque las bases de vulnerabilidades cambian. Sin proveedor/MFA productivos, resolución OQ-015/matriz funcional/#74 ni auditoría/outbox #29. El costo de locks/listas y bloat requiere medición antes del piloto.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79.** Presupuesto $0 sin modificaciones; no reintentar jobs, evadir restricciones ni presentar fallos pre-runner como éxito/fallo de código. El PR conserva Draft, sin merge/despliegue/cierre manual ni trabajo de #29.
