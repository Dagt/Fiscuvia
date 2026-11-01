# Validación reproducible — journal y outbox #29

Rama `issue-29-audit-journal-transactional-outbox`, base main `1f09a26fc0afbcc3498b825e1b3f676cda79d2f0` (#84 integrado). Este documento versiona comandos/mínimos; el PR y los summaries depurados registran el SHA final, resultados y tiempos reales. No se considera una corrida de worktree ni los 203 tests anteriores evidencia suficiente de #29.

## Entorno

Windows 11 build 26200; Oracle Java 25.0.3+9; Maven Wrapper 3.9.16; Docker Desktop daemon Linux 29.2.0, Ryuk activo/reuse false. PostgreSQL 18.6 y Keycloak 26.8.1 reales, imágenes fijadas por digest; Boot 4.1.2, Modulith 2.1.2, Flyway 12.4.0, Testcontainers 2.0.5, JUnit 6.0.3, Security 7.1.2 y Session 4.1.1. Node 24.13.1/npm 11.8.0/Playwright 1.64.1. Sin dependencias nuevas. Contenedores Linux no acreditan runner Ubuntu/Temurin remoto.

## Comandos desde raíz

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

POSIX: `./mvnw`, `PLAYWRIGHT_JSON_OUTPUT_FILE=../.ci-work/playwright.json npm run test:e2e -- --reporter=line,json` y Chromium `install --with-deps`; requiere unzip. Verificar código de salida, no continuar ante fallo. No ejecutar Maven/harness simultáneos sobre target. Compose usa sólo .env sintético ignorado y no imprime secretos; tests no dependen de él ni de un volumen persistente.

## Mínimos y evidencia

| Gate | Exigencia |
|---|---|
| Backend | **250** = 225 Surefire + 25 Failsafe, cero failed/errors/skipped, conserva 203 previas |
| AuditJournalTests | **44** nuevos casos con OIDC real/PG/runtime, datos y efectos HTTP/SQL observables, sin mocks de autenticación |
| JournalMigrationIT | **3**: upgrade V4→V5 una vez, checksums anteriores intactos, checksum alterado rechazado, DDL fallido rollback/reintento y scopes de entrega vacíos |
| Regresión | Todas las suites anteriores Modulith/RLS/roles/pool/Keycloak/CSRF/MFA/sesiones/invitaciones/API/observabilidad/JAR; tercer módulo audit sin ciclos |
| Frontend | npm ci/typecheck/lint/format/build, **37 Vitest**, **13 Playwright** de fundación + **5 OIDC** con Chromium/HTTPS/Keycloak reales, sin retries/flaky/omitidos |
| Contratos | Ambos OpenAPI y generación TS reproducible; **51** respuestas HTTP conformes + ocho schemas de identidad sobre HTTP real en memoria |
| tools/ci | Nueve pruebas de gates/contratos, incluida regresión de permisos POSIX del snapshot, sin reducir ninguno |
| Seguridad | Gitleaks historial/worktree, actionlint, zizmor, Trivy incluyendo npm dev y SBOM Maven test; gate HIGH/CRITICAL sin nuevas exclusiones |
| Artifacts | Summary/JUnit depurado únicamente, allowlist y scan; sin JAR/keystores/tokens/sesiones/fixtures/logs/reportes crudos |

Las 44 pruebas verifican: commit con actor/tenant/correlación confiables; rollback antes/después del efecto y después de append; falla al insertar journal/outbox revierte cambios; retry/replay y concurrencia con un solo efecto; bootstrap administrativo atribuido correctamente; emisión/revocación/aceptación reales con causación; suspensión/revocación conserva autoría y no duplica no-op; denegación global tras rollback, incluso exterior anidado; actor/prueba SQL falsificados; privilegios runtime/sesiones/HTTP; RLS y conexiones reutilizadas; payload RFC/secreto rechazado por CHECK (SQLSTATE 23514); canarios fuera de logs/respuestas; transporte sin negocio/cross-tenant; claims simultáneos, crash/restart/lease/backoff/exhaustion; efecto local+ack atómicos y dedupe downstream; versión no soportada; fencing de consumidores concurrentes; expiración durante efecto; revocación concurrente de membresía; journal fuera de transacción rechazado; oráculos de grants, append-only y eliminación de cerca RLS; intención/terminales de outbox protegidos.

Los roles de transporte son efímeros, NOSUPERUSER/NOBYPASSRLS/NOINHERIT y sin app/identity; aprobaciones sintéticas por OID/tenant sólo en DB desechable. No se agrega esa autoridad a Compose/JAR/runtime. Consumidor sintético escribe recibos únicos de prueba, nunca ejecuta trabajos fiscales. Una prueba simula efecto externo ya confirmado antes de ack y deduplica por event_id: demuestra at-least-once y su límite, no exactly-once.

Mutaciones de políticas/triggers/grants sólo ocurren en DB desechable, se restauran en finally y deben hacer fallar el oráculo. Para simular una versión futura se deshabilita temporalmente sólo el guard de intención dentro de una transacción administrativa de fixture y se restaura antes de commit; FORCE RLS y tenant fence siguen activos. No se usa un bypass de aislamiento para conseguir aprobación.

## Evidencia/limitaciones

Sin cambios visibles de UI ni nuevos endpoints de auditoría. Se conserva identidad navy/teal y se regeneran capturas reales de acceso/error de #27 como regresión, sin baseline conceptual inventado. La comparación exacta con la imagen original ausente no se afirma.

[Journal](../docs/security/AUDIT-JOURNAL.md) y [outbox/runbook](../docs/delivery/OUTBOX.md) describen los riesgos: no WORM ante DBA/BFF; fallos de almacenamiento de denegación producen rechazo+diagnóstico, no garantía de persistencia; autoridad de negocio/operación de workers, retención/alertas/carga y aprobación de lector pendientes. No se implementan #30 ni dominio #74. Se mantienen los avisos moderados heredados de tooling (#27/#28), sin audit fix --force; el PR informa la consulta actual HIGH/CRITICAL.

Presupuesto $0 sin modificaciones ni reruns/bypasses. Si GitHub rechaza antes del runner: **REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79.** No atribuir ese estado a una ejecución que sí inició pasos.

La primera ejecución de #29 ([run 41255185037](https://github.com/Dagt/Fiscuvia/actions/runs/41255185037), head 224a554) sí inició Ubuntu/Temurin. Security encontró un fallo de permisos del snapshot: mkdtemp crea 0700 en POSIX; root del contenedor sin DAC_OVERRIDE no puede atravesar el directorio del runner. Se reprodujo el mismo error Gitleaks en Docker Linux y se corrigió únicamente el snapshot allowlisted a 0755, sin abrir work/logs/credenciales ni quitar cap-drop=ALL. La regresión verifica permisos de lectura/traversal y que el padre privado no cambia. El resultado remoto del SHA final se registra en el PR; no se asume éxito ni bloqueo de billing.

Prueba adicional Linux del gate: imagen oficial Node 24.13.1 Alpine `node@sha256:4f696fbf39f383c1e486030ba6b289a5d9af541642fc78ab197e584a113b9c03`, `node --test tools/ci/gates.test.mjs`, con repositorio read-only, red deshabilitada, tmpfs temporal y cap-drop=ALL. Cinco gates, cero fallos/omitidos; no sustituye clean verify ni las nueve pruebas completas de tools/ci. En Windows fs.stat no representa bits POSIX execute: se comprueba lectura y traversal observable; en Linux también los bits de acceso de grupo/otros. No se deshabilita la prueba en ninguna plataforma.
