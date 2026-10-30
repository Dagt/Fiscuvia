# Estrategia de pruebas
Backend JUnit + Testcontainers PostgreSQL real: reglas dominio, estado fiscal, transacciones, Flyway, autorización, RLS y concurrencia. Contratos OpenAPI verificados. React con typecheck, lint, tests de componentes/interacción y E2E Playwright sobre app ejecutable.
## Escenarios obligatorios
- Dos tenants con mismo RFC sintético: sin acceso cruzado a API, DB, archivos, reportes, jobs ni cache.
- Obligaciones recurrentes idempotentes; cambios de estado con evidencia/rechazo, fechas límite revisadas.
- Pagos de honorarios distintos de pagos de impuestos; importes decimales y ajustes auditados.
- Archivos hostiles, XML con XXE, ZIP grande, MIME falso, accesos a URLs firmadas sin autorización.
- UI teclado, mobile, idiomas consistentes, loading/empty/error, capturas reales.
- Conector SAT futuro: reintentos, caducidad firma, fallas, límites y jobs reanudables.
## CI implementada en #23 (para revisión)

Ver [CI.md](CI.md) y `.github/workflows/ci.yml`. PRs a main, incluyendo Draft, push main y manual; sin filtros de rutas. Backend conserva sus 47 pruebas JUnit/Spring Modulith/PostgreSQL real, checksum/rollback Flyway y arranque del JAR; web ejecuta typecheck/lint/formato/build, 13 Vitest y 13 Playwright/axe en todos los tamaños existentes. Los verificadores rechazan errores, omisiones, reintentos/flaky y suites ausentes; las cifras mínimas deberán revisarse explícitamente si cambia la cobertura.

OpenAPI: sintaxis, estructura 3.1 y referencias internas, más fixtures negativos. No verifica implementación de endpoints ni resuelve decisiones del borrador (#24). Seguridad: historial y archivos actuales con Gitleaks, inventario sensible, actionlint/zizmor offline, vulnerabilidades HIGH/CRITICAL de npm (incluyendo dev) y Maven (incluyendo test) con Trivy local. Las pruebas de los gates también son obligatorias.

Artifacts de siete días: JUnit backend sin propiedades/stdout/stderr, resúmenes numéricos y SHA probado/origen. No subir raw reports, `.env`, JAR, SBOM, certificados, trazas ni datos fiscales. `CI Required` exige éxito de los cuatro checks, incluso si uno falla o se omite. Si GitHub no ejecuta, reportar **REMOTE CI NOT VERIFIED**, causa comprobada y comandos locales equivalentes; no deducir billing sólo por ausencia de workflows.

RLS/autorización/endpoints de negocio, build de imagen de aplicación y regresión visual aprobada son futuros: no existen implementaciones que #23 pueda acreditar. Los escenarios de producto arriba son gates de los issues correspondientes, no pruebas ya entregadas.
## Datos de prueba
Ficticios, marcados DEMO; nunca datos personales, XML fiscales reales, certificados ni llaves de producción.
