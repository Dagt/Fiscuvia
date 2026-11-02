# Validación reproducible — regresión multitenant #30

Rama `issue-30-cross-tenant-security-regression`, base main `f40a4980c1eaf268c16fcc39b2855dbcc71378a6` (#85). Este archivo versiona protocolo/mínimos y límites; los resultados, duraciones, SHA final y enlaces por job se registran en el PR y artifacts sanitizados de esa ejecución. Una compilación, corrida focal o resultado histórico no sustituye la regresión completa sobre el SHA final.

## Entorno y límites

Windows 11 build 26200; Oracle JDK 25.0.3+9, Maven Wrapper 3.9.16, Docker Desktop Linux 29.2.0/Ryuk activo/reuse false. PostgreSQL 18.6 y Keycloak 26.8.1 con digest fijado; Boot 4.1.2, Modulith 2.1.2, Flyway 12.4.0, Testcontainers 2.0.5, JUnit 6.0.3, Security 7.1.2, Session 4.1.1. Node 24.13.1/npm 11.8.0/Playwright 1.64.1. No dependencias nuevas. Ubuntu 24.04/Temurin 25.0.5+8 se acredita únicamente mediante ejecución remota, no por Docker Linux sobre Windows.

No cambia código productivo, V1–V5 ni contratos OpenAPI implementados. No se confirma una vulnerabilidad runtime nueva por el solo hecho de que un DBA pueda alterar políticas: las mutaciones prueban capacidad de detectar drift y defensa en profundidad. JAR sin fixtures/controladores/claves de pruebas; ninguna cuenta real ni datos fiscales.

## Comandos desde raíz (PowerShell)

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
npm run generate:api --prefix tools/ci
npm test --prefix tools/ci
node tools/ci/contracts.mjs --responses backend/target/contract-responses.json
node tools/ci/security.mjs
node tools/ci/reports.mjs backend
node tools/ci/reports.mjs web
node tools/ci/artifacts.mjs backend
node tools/ci/artifacts.mjs web
node tools/ci/artifacts.mjs security
git diff --check
```

Comprobar `$LASTEXITCODE` y detener ante fallo. POSIX: `./mvnw`, instalar Chromium `--with-deps`, variable Playwright con `export`; requiere unzip. No ejecutar clean verify y auth-browser simultáneamente sobre target. Todos los reportes se regeneran; `.ci-work` es privado y no artifact. Capturas históricas de tests no son una nueva aprobación visual; #30 no cambia UI. Los archivos regenerados se revisan y sólo se restauran baselines sin cambio intencional.

## Mínimos bloqueantes y evidencia

| Gate | Exigencia y salida publicable |
|---|---|
| Backend | 295 = 250 anteriores + 45 nuevas; 270 Surefire + 25 Failsafe, cero fallo/error/omitido |
| Arquitectura y distribución | Modulith sin ciclos, JAR real, test-classpath/llaves/emisor/controladores/SQL adversarial ausentes |
| Regresión transversal | 28 escenarios críticos por métodos exactos, no sólo totales; regression.json con SHA probado/origen |
| Cobertura diferida | Tres contratos futuros, una verificación operativa pendiente y tres decisiones; siete NOT VERIFIED, jamás PASS |
| Frontend | 37 Vitest, 13 Playwright de fundación, cinco OIDC reales, sin retries/flaky/omitidos; typecheck/lint/format/build |
| Contratos | Borrador e implementado válidos; tipos TS reproducibles; 51 respuestas conformes y ocho schemas de identidad sobre HTTP real en memoria |
| tools/ci | 16 = nueve anteriores + siete nuevos; contador mínimo y rechazo de método crítico retirado, cobertura diferida falsificada y metadata arbitraria |
| Seguridad | Gitleaks 8.30.1 historial/worktree; actionlint 1.7.12; zizmor 1.31.1 offline; Trivy 0.76.1 con npm dev y SBOM Maven test, HIGH/CRITICAL bloqueantes sin exclusiones nuevas |
| Artifacts | Sólo summary/JUnit depurado/regression.json backend y summary web/security, scan Gitleaks, siete días; sin cuerpos HTTP, raw logs, sesiones, tokens, certificados, SBOM, JAR o SQL/filas |

La [matriz previa y ejecutable](../docs/security/CROSS-TENANT-REGRESSION.md) separa controles efectivos de contratos futuros/decisiones. [Mutaciones](../docs/security/NEGATIVE-TESTING.md) se realizan sólo en base desechable, restauran finally y vuelven a comprobar el control. Auth HTTP usa Keycloak real y callback productivo; no filtros ficticios ni H2/mocks de PostgreSQL. Efectos, rollback, lock/revocación, replay, actores y outbox se comprueban sobre filas reales.

## Publicación y riesgos residuales

#79 está resuelto después de #85. Presupuesto $0 intacto; no cambiar billing, protecciones, permisos o workflows para evadir fallos. Verificar cinco jobs `Backend CI`, `Web CI`, `OpenAPI CI`, `Security CI`, `CI Required` del SHA final y enlazarlos en PR Draft. Pending/skipped/failure no son éxito. Si vuelve rechazo antes de runner, registrar REMOTE CI NOT VERIFIED y respuesta concreta, junto con evidencia local; no atribuir automáticamente a billing.

No se acredita WORM, seguridad frente a DBA/IdP/BFF comprometidos, carga sostenida, restauración de infraestructura, storage documental, cache de negocio ni worker fiscal. Pendientes #74/OQ-015/proveedor y MFA productivo conservan sus gates. Antes de la siguiente etapa, revisar esos gates y mantener los casos exactos/contratos de seguridad al incorporar módulos; no avanzar automáticamente a #31.
