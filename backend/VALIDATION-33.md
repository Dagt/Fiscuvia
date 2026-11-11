# Validación reproducible #33 — corte de correo local/test

Base `19d6ec043ccbfda3c75c39b3e5428a72666cd3ba`, rama `issue-33-team-management`. [Alcance autorizado y garantías](../docs/security/LOCAL-EMAIL-INVITATIONS-33.md), [pendientes de política](../docs/security/TEAM-POLICY-PROPOSAL-33.md), [cartera diferida](../docs/architecture/TEAM-MANAGEMENT-33.md). El issue completo no está cerrado: directorio/roles/reactivación necesitan aprobación y cartera conserva #74.

Mínimos efectivos tras agregar las suites: **416 Java =375 Surefire+41 Failsafe**,107 Vitest,61 Playwright de fundación/presentación,18 OIDC y21 tools/ci. Cero fallos/omitidas/reintentos. Se conservan todos los mínimos de main #32. La migración V10 no cambia V1–V9 ni concede nuevos permisos administrativos; los positivos usan MEMBERSHIP_INVITE real.

## Comandos

Desde raíz, Java25, Maven Wrapper3.9.16, Node/npm dentro de engines/.nvmrc y Docker Linux/Ryuk accesible. PowerShell usa backend/mvnw.cmd y argumentos -D entre comillas; POSIX usa backend/mvnw.

```sh
backend/mvnw -f backend/pom.xml -B -ntp clean verify
node tools/ci/reports.mjs backend
node tools/ci/contracts.mjs --responses backend/target/contract-responses.json
backend/mvnw -f backend/pom.xml -B -ntp org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -DincludeTestScope=true
npm ci --prefix web
npm run typecheck --prefix web
npm run lint --prefix web
npm run format:check --prefix web
npm test --prefix web -- --reporter=json --outputFile=../.ci-work/vitest.json
npm run build --prefix web
PLAYWRIGHT_JSON_OUTPUT_FILE=../.ci-work/playwright.json npm run test:e2e --prefix web -- --reporter=line,json
node tools/ci/auth-browser.mjs
node tools/ci/reports.mjs web
npm ci --prefix tools/ci --ignore-scripts
npm test --prefix tools/ci
npm run generate:api --prefix tools/ci
npm run openapi --prefix tools/ci
git diff --exit-code -- web/src/shared/api/schema.d.ts web/src/shared/api/contract-metadata.ts
node tools/ci/security.mjs
node tools/ci/artifacts.mjs backend
node tools/ci/artifacts.mjs web
node tools/ci/artifacts.mjs security
git diff --check
```

PowerShell asigna `$env:PLAYWRIGHT_JSON_OUTPUT_FILE='../.ci-work/playwright.json'`. Instalar Chromium fijado con playwright install chromium (--with-deps en Linux). Auth-browser compila el harness: no ejecutarlo simultáneamente con Maven clean verify sobre backend/target. Las capturas públicas son opt-in con `$env:FISCUVIA_TEAM_CAPTURE='1'` antes de auth-browser, sobre fuente de aplicación confirmada en Git; quitar la variable para el gate ordinario. Nunca publicar handoff, logs completos, tokens o reportes de navegador como artifacts.

## Evidencia exigida

EmailInvitationTests27: callback OIDC y contactos verificados, exact issuer/subject, email cambiado sin retarget, destinatarios inelegibles con error seguro, primera respuesta secreta/replay sin secreto, concurrencia de comandos/destinatario, revocación/expiración/consumo, roles/MFA/tenant/CSRF/DTO, cuotas V9, ambos sinks, privacidad de logs/sesiones, firma por propósito y guardas. EmailInvitationMigrationIT3: upgrade V9/checksum/ejecución única y rollback/reintento. Ningún mock sustituye esos efectos HTTP/SQL.

Se encontró Docker Desktop detenido al iniciar la primera ejecución local; esa ejecución fallida no cuenta como PASS. Se inició el daemon normal sin cambiar perfiles ni sustituir Testcontainers. El primer escenario de navegador de carga falló por retirar un route handler antes de que liberara su respuesta real; se corrigió mediante una barrera explícita y handler de un uso, no con sleeps o respuestas inventadas. Lint/formato/typecheck y tests deben aprobar después de esas correcciones.

Los resultados finales, versiones efectivas, duración, SHA, fallos/omitidas y enlaces de los cinco jobs se publican en el PR Draft y summaries depurados del mismo HEAD. Este documento no acredita por sí solo CI verde ni el alcance pendiente de equipo. Presupuesto, billing, protecciones y workflows no se modifican; no merge/despliegue/cierre manual.
