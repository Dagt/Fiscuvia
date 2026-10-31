# Validación de identidad, sesiones y membresías #27

Implementación en `issue-27-oidc-secure-sessions-memberships`, PR Draft [#83](https://github.com/Dagt/Fiscuvia/pull/83), Refs #27. Base main comprobada: `05b5b18b157cd88d8f76967f9d2d3b179960f7aa`. La [evidencia documental anterior](VALIDATION-27-PROPOSAL.md) no acredita autenticación.

El SHA final efectivamente probado se registra en el cuerpo del PR y en `testedSha` de los summaries sanitizados; no se confunde el nombre de rama o un resultado de worktree con evidencia del commit final. Los reportes generados son locales/ignorados. Esta guía versiona los comandos y mínimos; el PR registra sus resultados finales verificables y tiempos sobre ese SHA.

## Entorno y versiones efectivas

Windows 11 build 26200; Oracle Java 25.0.3+9, Maven Wrapper 3.9.16; Docker Desktop Linux daemon 29.2.0 con Ryuk y reutilización deshabilitada. PostgreSQL **18.6**, Flyway **12.4.0**, Testcontainers **2.0.5**, JUnit **6.0.3**, Boot **4.1.2**, Modulith **2.1.2**, Security **7.1.2**, Session **4.1.1**, Keycloak **26.8.1**. Node **24.13.1**, npm **11.8.0**, Playwright **1.64.1**. El runner remoto Ubuntu/Temurin aún no está verificado; no equiparar contenedores Linux a una ejecución nativa de Actions.

Imágenes fijadas por digest en `PostgresTestDatabase`/Compose y `KeycloakTestProvider`. Usuarios, contraseñas, OTP, claves de firma, HMAC y certificado HTTPS se generan para cada fixture. No hay credenciales de producción, proveedores externos, datos fiscales ni fallback H2. Las pruebas del JAR no usan el classpath de tests.

## Comandos reproducibles

Desde raíz, sin deshabilitar suites:

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

POSIX: sustituir `./mvnw.cmd` por `./mvnw`; usar `PLAYWRIGHT_JSON_OUTPUT_FILE=../.ci-work/playwright.json npm run test:e2e -- --reporter=line,json`. Ubuntu necesita `npx --no-install playwright install --with-deps chromium`. Compose config requiere el `.env` sintético generado/actualizado mediante los scripts de infra y nunca imprime su contenido. La infraestructura de los tests es independiente de ese `.env`/volumen.

## Mínimos bloqueantes y evidencia observable

| Gate | Mínimo / verificación |
|---|---|
| Maven clean verify | **161**: 142 Surefire + 19 Failsafe; conserva los 135 anteriores; cero fallos/errores/omisiones |
| OidcAuthenticationTests | **22**: flujos reales de Code/PKCE/OTP; firma/JWKS/issuer/audience/expiración; state/nonce/PKCE manipulados; CSRF, cookies, fixation, logout, expiraciones y sesiones independientes; autorización/RLS, bootstrap, invitaciones y canarios |
| Flyway | V1/V2 intactas; V2→V3 una vez, checksums conservados, alteración rechazada, rollback de V3 y reintento; upgrade de rol idempotente |
| RLS previo | 20 escenarios + 2 de concurrencia/pool conservados con actores internos controlados y autorización SQL previa; sin filtros HTTP ficticios ni bypass productivo |
| JAR real | 11 pruebas: arranque, health/info, /me protegido, fallos de DB/configuración/privilegios, métricas privadas, no exportación y archivo sin fixtures/llaves |
| Web | npm ci, typecheck, ESLint, Prettier, **37** Vitest, build, **13** Playwright anteriores + **5** OIDC reales; cero omitted/flaky/retries |
| Contratos | Ambos OpenAPI 3.1 válidos, generación TypeScript reproducible; 51 respuestas HTTP originales conformes y ocho schemas de identidad verificados contra respuestas reales en memoria |
| Tools/CI | **8** tests, incluyendo fixtures de contrato/seguridad/reportes que deben fallar |
| Seguridad | Gitleaks historial+worktree, actionlint, zizmor y Trivy npm dev/Maven test SBOM HIGH/CRITICAL, sin exclusiones globales |
| Artifacts | Allowlist summary/JUnit depurado, escaneados; sin logs crudos, .env, certificados, tokens, fixtures, SBOM, trazas o sesiones |
| UX | [PNG reales 1440×900 y 390×844, error/sin membresías/autorización](../docs/ux/evidence/issue-27/README.md), Axe/foco/overflow |

Se verifican contenidos persistidos antes/después del callback: principal mínimo y ausencia de ID/access/refresh tokens, OidcUser, OAuth2AuthorizedClient y secretos. Se prueba SQL runtime sin predicados de tenant, actor/GUC forjados, UUID ajeno, conexión reutilizada, commit/rollback, FK/índices y denegación de la entrada SQL si falta autorización. MFA ausente/futuro/vencido no concede privilegios; el perfil local se apoya en OTP nativo real, no claims constantes. Dos aceptaciones concurrentes producen exactamente un éxito.

Correcciones encontradas durante desarrollo: contenido UTF-8 en errores de filtros, diagnóstico tipado de rechazo de privilegios, eliminación del acceso runtime a la función V2 sin autorización, aislamiento de cookies IdP/BFF y cierre explícito del JVM real en Windows. Se conservaron las pruebas; el harness cierra sus propios contenedores y deja intactos servicios ajenos. Los cambios de expectativa (401 en /me; 403 de CSRF antes de MVC) corresponden a la seguridad implementada y están documentados.

## Estado remoto y límites

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79.** Presupuesto $0, sin cambios de pagos/permisos/protecciones ni bypasses. No considerar un fallo pre-runner un fallo de código ni un éxito remoto.

Proveedor de producción, semántica MFA/recuperación/revocación federada, rotación/custodia operativa y correo de invitaciones siguen pendientes. #28/#33 y el gate fiscal #74 no se implementan. No se marca Ready, no se hace merge, no se cierra #27 ni se despliega. Se requiere revisión humana de seguridad del PR y de las decisiones productivas abiertas.
