# #27 — validación de la fase de propuesta

Base main: `05b5b18b157cd88d8f76967f9d2d3b179960f7aa`. Rama: `issue-27-oidc-secure-sessions-memberships`. Fecha: 2026-10-30. El PR y los resúmenes locales identifican el SHA final de esta fase; no hay un SHA autorreferente en este archivo.

## Qué se valida

Este cambio es exclusivamente documental: evaluación de proveedores, modelo BFF/sesiones y bootstrap de identidad para revisión. No cambia Java/React, POM/lockfiles, V1/V2, contratos OpenAPI, RLS, roles SQL, configuración, workflows ni tests. ADR-005 conserva Proposed y OQ-014 Open. El issue #27 **no está implementado ni listo para revisión final**.

Se ejecuta la regresión existente para comprobar la base y los gates sobre el SHA de esta propuesta: Maven clean verify + SBOM test scope; 135 backend (120 Surefire + 15 Failsafe), 21 Vitest y 13 Playwright; instalación/typecheck/lint/format/build; siete tests de herramientas CI; ambos OpenAPI/generación exacta y 51 respuestas HTTP conformes; Gitleaks, actionlint/zizmor, Trivy HIGH/CRITICAL y artifacts depurados; git diff --check. Los resultados y tiempos finales se registran en el PR después de ejecutar. **Esas pruebas no verifican login, MFA, sesiones, invitaciones ni membresías de #27.** No se han omitido tests nuevos: todavía no existen.

Entorno base: Windows 11 build 26200, Java 25.0.3+9, Maven Wrapper 3.9.16, Docker Linux 29.2.0/Ryuk activo, PostgreSQL 18.6/Flyway 12.4.0/Testcontainers 2.0.5, Boot 4.1.2/Modulith 2.1.2/JUnit 6.0.3; Node 24.13.1/npm 11.8.0/Playwright 1.64.1. No se acredita runner Ubuntu/Temurin ni compatibilidad ejecutable de Spring Security/OIDC aún.

## Comandos

Desde raíz en PowerShell, comprobar exit codes:

```powershell
git rev-parse HEAD
cd backend
.\mvnw.cmd -B -ntp clean verify org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom '-DincludeTestScope=true' '-DoutputFormat=json'
cd ..
npm ci --prefix tools/ci --ignore-scripts
npm test --prefix tools/ci
npm run openapi --prefix tools/ci
node tools/ci/contracts.mjs --responses backend/target/contract-responses.json
cd web
npm ci
npm run typecheck
npm run lint
npm run format:check
npm test -- --reporter=default --reporter=json --outputFile=../.ci-work/vitest.json
npm run build
npx --no-install playwright install chromium
$env:PLAYWRIGHT_JSON_OUTPUT_FILE='../.ci-work/playwright.json'
npx --no-install playwright test --reporter=list,json
cd ..
node tools/ci/reports.mjs backend
node tools/ci/reports.mjs web
npm run security --prefix tools/ci
node tools/ci/artifacts.mjs backend
node tools/ci/artifacts.mjs web
node tools/ci/artifacts.mjs security
git diff --check
```

Crear `.ci-work` si no existe. Docker debe estar disponible; no deshabilitar Ryuk ni reutilizar bases. Tests autoaprovisionados, sin necesitar .env ni DB de producción. POSIX: `./mvnw`, `playwright install --with-deps chromium` y variable `PLAYWRIGHT_JSON_OUTPUT_FILE=...` en la invocación. Ver [CI](../docs/delivery/CI.md).

## Pendiente de autorización/implementación

No hay capturas de login porque no se ha construido. Playwright sólo ejecuta la UI existente; sus capturas históricas no son evidencia de autenticación. No hay migraciones de identidad/sesión, proveedor contratado/configurado, emisor de prueba agregado, credenciales, MFA válido, invitación ni bootstrap implementados. La [propuesta concreta](../docs/decisions/issue-27-oidc-session-proposal.md) y su sección de aprobaciones son la entrega revisable de esta fase. No mergear como cumplimiento del issue ni marcar Ready.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79.** Presupuesto $0 intacto, sin reintentos/bypasses. No se hace merge, despliegue, cierre del issue ni trabajo en otro módulo.
