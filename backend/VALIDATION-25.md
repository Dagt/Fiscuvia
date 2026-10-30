# Evidencia local — issue #25

Fecha: 2026-10-30. Base auditada y actualizada desde `main`: `4d43442bd62e9830eb9f29c3ef7c5919963ad1fa` (PR #80). Rama: `issue-25-observability-logging-health`. El SHA final probado se identifica en el PR y en los resúmenes locales generados con `git rev-parse HEAD`; no se guarda un SHA autorreferente en este archivo.

## Entorno

Windows 11, build 26200; Oracle JDK 25.0.3+9-LTS-195; Maven Wrapper 3.9.16 (distribución con checksum); Docker Desktop con daemon Linux 29.2.0 y Ryuk activo. PostgreSQL 18.6 (`18.6-alpine3.24`, digest conservado de #22), Flyway 12.4.0, Testcontainers 2.0.5, JUnit 6.0.3, Spring Boot 4.1.2 y Modulith 2.1.2. Micrometer 1.17.2, Tracing 1.7.2 y OpenTelemetry 1.62.0 administrados por Boot. Node 24.13.1, npm 11.8.0, Playwright 1.64.1 / Chromium instalado por su lockfile.

No se acredita ejecución nativa Ubuntu/Temurin: el workflow conserva Ubuntu 24.04/Temurin 25.0.5+8, pendiente #79. PostgreSQL y scanners sí se ejecutan en contenedores Linux locales.

## Resultados que deben reproducirse antes de publicar

| Verificación | Resultado local |
|---|---|
| Maven clean verify | 108 pruebas: 96 Surefire + 12 Failsafe, cero fallos/errores/omisiones |
| Arquitectura | Tres pruebas Modulith, incluido rechazo de ciclos/acceso interno |
| PostgreSQL/Flyway | DB real desechable, checksum/rollback/historial/privilegios; sin H2 |
| JAR real | Diez pruebas, incluidas ocho anteriores; fallo de configuración/conexión, métricas separadas y cero exportación al destino señuelo |
| Observabilidad | JSON parseado, proyección segura, MDC/restauración, 20 solicitudes concurrentes, timeout HTTP real, cuerpos/headers/query/excepciones canario, spans en memoria y sampling |
| Disponibilidad | PostgreSQL detenido: readiness 503 DOWN sin detalles, liveness 200, gauge DB 0 |
| Web | npm ci, typecheck, lint, Prettier, 21 Vitest, build, 13 Playwright/axe, cero omisiones/reintentos |
| Contratos | Dos OpenAPI 3.1 válidos; referencias y generación exacta; 51 respuestas HTTP reales conformes a AJV |
| Herramientas CI | Siete pruebas, incluidos fixtures negativos que deben ser rechazados |
| Seguridad | Gitleaks historial/fuentes, inventario sensible, actionlint, zizmor y Trivy HIGH/CRITICAL con npm dev/Maven test |
| Artifacts | Sólo resumen SHA y JUnit depurado; escaneo de los tres grupos; sin stdout, propiedades, logs, trazas, SBOM, JAR ni .env |
| Git | diff --check sin errores; sin migraciones, cambios UI/negocio, cambios de workflows ni ampliación de perfiles |

Los resultados finales se publican sólo después de ejecutar estos comandos sobre el commit final. Los logs y reportes crudos permanecen ignorados; no adjuntarlos al PR.

## Comandos reproducibles

Desde la raíz, con Docker Linux accesible y sin deshabilitar Ryuk:

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
npx playwright install chromium
$env:PLAYWRIGHT_JSON_OUTPUT_NAME='../.ci-work/playwright.json'
npx playwright test --reporter=list,json
cd ..
node tools/ci/reports.mjs backend
node tools/ci/reports.mjs web
npm run security --prefix tools/ci
node tools/ci/artifacts.mjs backend
node tools/ci/artifacts.mjs web
node tools/ci/artifacts.mjs security
git diff --check
```

POSIX: `./mvnw` en backend y `PLAYWRIGHT_JSON_OUTPUT_NAME=../.ci-work/playwright.json npx playwright test --reporter=list,json` en web; instalar Chromium con `npx playwright install --with-deps chromium` en Ubuntu. El resto de comandos Node/npm es igual. El SBOM local incluye dependencias de test para Trivy y no se publica.

## Hallazgos y límites

- Los ocho tests anteriores del JAR se conservan: esperan códigos JSON seguros en lugar de mensajes/stack internos. El código diagnóstico de configuración contempla fallos de conversión numérica.
- El bootstrap registra el listener operacional mediante `META-INF/spring.factories`; no introduce dependencia del módulo raíz hacia tipos internos de platform. Modulith conserva sus reglas.
- Spring cierra la observación en el callback de timeout antes de escribir el error HTTP: el listener propio registra el error antes de ese cierre y la convención asigna 500 si todavía no se escribió un estado de error. La regresión comprueba el span de la misma traza, no sólo que exista algún span.
- Los mensajes de librerías se proyectan a RUNTIME_LOG; se pierde detalle no autorizado. Huellas de ubicaciones no registradas pueden coincidir. Ver [política y runbook](../docs/delivery/OBSERVABILITY.md).
- No hay exporter en runtime, autenticación administrativa, collector, alertas, jobs, métricas por tenant ni despliegue. Métricas opt-in sólo en máquina confiable por loopback. Los gauges describen última comprobación/arranque, no disponibilidad histórica.
- No se modifican contratos públicos, tablas fiscales, RLS, OIDC, esquema #74 ni ADR Proposed. Frontend sin cambios visibles; Playwright conserva la evidencia real existente.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79.** Presupuesto $0 intacto. No se deshabilitan workflows ni se reintenta ejecución remota; el owner conserva la revisión y merge. Ningún fallo pre-runner se presenta como éxito de CI.
