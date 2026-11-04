# Validación reproducible — #31

Base main879faee5b2872c1783859b998bf09119257f2472; rama issue-31-secure-tenant-provisioning-branding. [Diseño/autoridad](../docs/architecture/TENANT-PROVISIONING.md), [capturas/procedencia](../docs/ux/evidence/issue-31/README.md), [CI completa](../docs/delivery/CI.md). El SHA final probado, duración, resultados locales y enlaces de los cinco jobs se publican en el PR Draft y en los summaries allowlisted ligados a ese HEAD; este archivo no intenta incluir su propio hash recursivamente.

## Gates y herramientas

Sin paquetes nuevos. Java25.0.3, Boot4.1.2, Modulith2.1.2, MavenWrapper3.9.16, Flyway12.4, Testcontainers2.0.5; PostgreSQL18.6-alpine3.24 y Keycloak26.8.1 con digest existente. Node24.13.1/npm11.8.0, React19.3.1/TS5.9.3, Vitest5.0.5, Playwright1.64.1/Chromium156.0.8078.4. Windows11Pro local y Ubuntu del runner remoto son evidencias distintas. Presupuesto $0 sin cambios.

Requerido: **332 Java (304 Surefire+28 Failsafe),85 Vitest,33 Playwright de fundación/catálogo,13 OIDC y18 tools/ci**, cero omitidas/fallos/reintentos. `reports.mjs` conserva cada mínimo anterior y añade las suites de provisioning. Artifacts sólo summary, JUnit depurado y matriz #30; no cuerpos HTTP, logs, screenshots de proveedor, certificados, handoff, SBOM, JAR o credenciales.

Desde raíz, comandos POSIX (PowerShell: usar backend/mvnw.cmd y entrecomillar argumentos -D que contengan puntos/comas):

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
npm run openapi --prefix tools/ci
npm run generate:api --prefix tools/ci
git diff --exit-code -- web/src/shared/api/schema.d.ts web/src/shared/api/contract-metadata.ts
node tools/ci/security.mjs
node tools/ci/artifacts.mjs backend
node tools/ci/artifacts.mjs web
node tools/ci/artifacts.mjs security
node tools/ux/validate-tenant-provisioning.mjs
git diff --check
```

Instalar Chromium fijado antes de E2E (`npx --no-install playwright install chromium` dentro de web; Ubuntu añade --with-deps). PowerShell usa `$env:PLAYWRIGHT_JSON_OUTPUT_FILE='../.ci-work/playwright.json'`; revisar LASTEXITCODE tras cada paso. No ejecutar auth-browser test-compile y clean verify simultáneamente sobre backend/target. El wrapper completo prueba arranque JAR, Modulith, RLS, sesiones/OIDC/MFA, autorización, privacidad y journal/outbox; no se sustituyen por sólo la suite nueva. Security ejecuta Gitleaks/actionlint/zizmor/Trivy dev/test con digests existentes y dependencias de prueba en SBOM.

## Escenarios nuevos y hallazgos corregidos

TenantProvisioningTests usa sesiones Keycloak reales y PostgreSQL con los roles efectivos. Comprueba alta interna/owner verificado, ausencia de API anónima/operador, privilegios runtime/sesiones, reintento concurrente, nombres iguales aislados, perfil propio, body/UUID falsificados, CSRF/constraints, versión/replay, roles/MFA, suspensión/reactivación, rollback, journal/outbox fallidos, revocación, bloqueos observables y canarios de nombre/contacto ausentes de logs/payloads. Antes de instalar su política sintética de edición, exige que la migración no haya concedido TENANT_SETTINGS_MANAGE permanentemente sin aprobación.

Oráculos adicionales: GRANT SELECT indebido, pérdida de FORCE y EXECUTE administrativo disparan la guarda real; retirar el filtro explícito del directorio también. La RLS de membresía es un segundo control: permanece aislada incluso al retirar ese filtro. No se informa una fuga inexistente por haber mutado sólo una defensa redundante. Se restaura cada cambio en finally, sólo en contenedores desechables.

ProvisioningMigrationIT conserva checksums V1–V5, aplica V6 una vez, incorpora una raíz V5 anterior sin inventar su nombre y prueba DDL transaccional fallido/reintento. No se edita ninguna migración histórica.

La regresión detectó un deadlock real entre upgrades FOR SHARE→FOR UPDATE de dos ediciones; se corrigió tomando el bloqueo exclusivo de raíz desde el inicio, antes de membresía/política. La prueba exige un 200 y un409, nunca un500. Las pruebas OIDC de navegador inicialmente reutilizaban el OTP de una misma identidad en el mismo intervalo; Keycloak lo rechazó correctamente. Se aislaron las identidades por escenario y se espera el callback antes de consultar /me. No se relajó OTP, state, PKCE, nonce ni MFA; no se añadió un sleep para ocultar el fallo.

La revisión final añadió prevalidación de identidad/membresía/capacidad/MFA y rechazo de cambio de contexto antes de adquirir locks de raíz. La versión inicial podía retener un lock de perfil con una llamada SQL runtime rechazada; no exponía ni modificaba filas, pero abría una frontera de disponibilidad innecesaria. NOWAIT demuestra que las denegaciones no bloquean el perfil; dos lectores demuestran que cambiar de capacidad se rechaza sin upgrade de lock/deadlock. La prevalidación se repite con locks antes de permitir trabajo, conservando la protección contra revocaciones concurrentes. El oráculo #30 de supresión de membresía retira/restaura también esta defensa redundante, sin reducir pruebas.

Los E2E validan schemas de perfiles/respuestas y errores sobre HTTP real, cambio de despacho, guardar/cancelar/confirmar, conflicto, error recuperable, reflow, revocación durante lectura y suspensión. Los unitarios sólo prueban presentación/decodificación/cache; no sustituyen seguridad real. Las capturas incluyen desktop/móvil y estados verificados, según manifest.

## Estado funcional pendiente

La propuesta de grant permanente owner+MFA requiere aprobación expresa y permanece DENY. Los tests/capturas positivos usan una política sintética sólo en su base desechable. No se declara completada esa aprobación por tener CI verde. Proveedor/MFA productivo, matriz completa, almacenamiento de logos y OQ-016 siguen pendientes. No se implementan #32/#33, workers o tablas fiscales #74. El PR permanece Draft para revisión humana; no merge ni despliegue.
