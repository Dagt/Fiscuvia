# Evidencia local — issue #26

Fecha: 2026-10-30. Base main auditada: `e47dc858509dd652db693b035256214b7526a366` (PR #81 integrado). Rama: `issue-26-postgresql-tenant-rls`. El PR y `.ci-artifacts/*/summary.json` identifican el SHA final probado; no se almacena un SHA autorreferente en este archivo. Validación final obligatoria antes de publicar el Draft.

## Entorno y herramientas

Windows 11 build 26200; Oracle Java 25.0.3+9-LTS-195; Maven Wrapper 3.9.16; Docker Desktop con daemon Linux 29.2.0, Ryuk activo y sin reuse. PostgreSQL 18.6-alpine3.24 con digest existente `10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5`; Flyway 12.4.0; Testcontainers 2.0.5; JUnit 6.0.3; Boot 4.1.2; Modulith 2.1.2. Sin dependencias nuevas ni JPA. Micrometer/Tracing/OpenTelemetry conservan 1.17.2/1.7.2/1.62.0.

Node 24.13.1, npm 11.8.0, Playwright 1.64.1 y Chromium instalado mediante el paquete fijado. Scanners Docker con digest: Gitleaks 8.30.1, actionlint 1.7.12, zizmor 1.31.1 y Trivy 0.76.1. PostgreSQL/scanners corren en contenedores Linux; no se acredita backend nativo Ubuntu/Temurin, pendiente #79.

## Resultados locales y tiempos

| Gate | Evidencia esperada/reproducida para el SHA del PR |
|---|---|
| Maven clean verify | **135**: 120 Surefire + 15 Failsafe, cero fallos/errores/omisiones; conserva las 108 anteriores |
| RLS | 20 casos con rol runtime real: ausencia/forja de contexto, lectura/CRUD cruzados, mismo RFC DEMO A/B, FK/unique/índices, catálogo y permisos |
| Pool/transacciones | Pool máximo 1 y PID idéntico tras commit/rollback/excepción; nesting mismo tenant, cambio rechazado/rollback exterior, savepoints/read-only denegados, postcondición y sesión falsificada |
| Concurrencia | Cuatro PIDs simultáneos con barrera, 80 lecturas y 32 operaciones mixtas sobre pool reutilizado; cero filas/contextos ajenos |
| Resistencia | Deshabilitar RLS, eliminar o relajar la cerca hace fallar deliberadamente el oráculo; deriva de FORCE/grants dispara guarda; restauración en finally |
| Flyway | V1 intacta; upgrade V1→V2, reejecución cero, checksum V2 alterado rechazado, DDL completo revertido ante error y reintento válido; tres IT nuevas |
| HTTP/privacidad | Dos tests nuevos: header/query/body no crean contexto; rechazo seguro RFC 9457/correlación/log fijo; sin endpoint público tenant |
| JAR/contratos/observabilidad | Diez IT del JAR y todas las suites anteriores conservadas; PostgreSQL real/health; 39 tests API y 51 respuestas AJV |
| Web | npm ci, typecheck, lint, Prettier, 21 Vitest, build y 13 Playwright/axe; sin fallos/omisiones/flaky/reintentos |
| tools/ci/contratos | Siete tests, dos OpenAPI válidos, generación TS/metadatos exacta y respuestas conformes |
| Seguridad | Gitleaks historial/fuentes, actionlint/zizmor, inventario sensible, Trivy HIGH/CRITICAL npm dev/Maven test; sin exclusiones nuevas |
| Artifacts/Git | Sólo resúmenes SHA/JUnit depurado, tres grupos escaneados; compose config --quiet y git diff --check limpios |

Primera ejecución completa de desarrollo: 134 tests backend aprobados en **2 min 24 s**; se añadió después una comprobación de catálogo a la suite RLS (total 135). La ejecución focalizada posterior pasó 23 tests en 28.5 s. El tiempo y resultado de la ejecución final sobre el commit se registran en el PR; estos tiempos intermedios no sustituyen esa ejecución.

## Comandos sobre el SHA final

Desde la raíz en PowerShell; comprobar exit code y detenerse ante fallos:

```powershell
git rev-parse HEAD
docker compose --env-file infra/.env -f infra/compose.yaml config --quiet
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

El comando Compose requiere el `.env` sintético local ignorado; no publicar su expansión. Tests no necesitan Compose ni ese archivo: aprovisionan contenedores aislados y no tocan otros proyectos. POSIX: `./mvnw`, Chromium `install --with-deps chromium`, y `PLAYWRIGHT_JSON_OUTPUT_FILE=../.ci-work/playwright.json npx --no-install playwright test --reporter=list,json`. Crear `.ci-work` si no existe. SBOM/raw reports/logs se conservan ignorados y no se adjuntan al PR.

## Límites y decisiones

El [modelo físico/SQL/puerto transaccional](../docs/data-model/tenant-foundation-26.md) diferencia infraestructura real, fixtures y modelo Proposed. No hay tablas fiscales ni cambios en V1/ADR Accepted. La tabla memberships se difiere expresamente a #27 por OQ-014/022; no se inventa un sujeto OIDC, membresía sin identidad o sesión. #28 debe autorizar recursos; #30 mantiene regresión transversal pendiente. No se cambian perfiles remotos, endpoints públicos, estilos ni contratos OpenAPI.

Runtime no es propietario/superuser/BYPASSRLS y los datos de fixtures se insertan con contexto aun usando migrador/FORCE. Las mutaciones deliberadas son negativas aisladas, no un bypass para lograr tests verdes. Ningún test se deshabilita cuando falta Docker. El registro privado se invalida al terminar cada transacción y limpia metadatos oportunistamente; implica escrituras, rechaza transacciones read-only y necesita evaluación de carga/autovacuum antes del piloto. RLS no reemplaza autorización ni protege frente a código/credenciales runtime comprometidos al elegir contexto inicial.

PostgreSQL suprime errores SQL ordinarios y parámetros/sentencias para evitar valores privados en logs. Se comprueba con un canario real y se conserva diagnóstico operacional seguro en aplicación. Revisión de logs administrativos/catastróficos y retención sigue siendo requisito previo al piloto.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79.** Presupuesto $0 intacto; workflows/gates conservados, sin bypasses, despliegue, merge ni cierre manual del issue.
