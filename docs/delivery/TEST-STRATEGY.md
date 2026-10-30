# Estrategia de pruebas
Backend JUnit + Testcontainers PostgreSQL real: reglas dominio, estado fiscal, transacciones, Flyway, autorización, RLS y concurrencia. Contratos OpenAPI verificados. React con typecheck, lint, tests de componentes/interacción y E2E Playwright sobre app ejecutable.
## Escenarios obligatorios
- Dos tenants con mismo RFC sintético: sin acceso cruzado a API, DB, archivos, reportes, jobs ni cache.
- Obligaciones recurrentes idempotentes; cambios de estado con evidencia/rechazo, fechas límite revisadas.
- Pagos de honorarios distintos de pagos de impuestos; importes decimales y ajustes auditados.
- Archivos hostiles, XML con XXE, ZIP grande, MIME falso, accesos a URLs firmadas sin autorización.
- UI teclado, mobile, idiomas consistentes, loading/empty/error, capturas reales.
- Conector SAT futuro: reintentos, caducidad firma, fallas, límites y jobs reanudables.
## CI definida en #23 e integrada; verificación remota pendiente #79

Ver [CI.md](CI.md) y `.github/workflows/ci.yml`. PRs a main, incluyendo Draft, push main y manual; sin filtros de rutas. #24 conserva la cobertura anterior y amplía a 90 pruebas backend (80 Surefire + 10 Failsafe), 21 Vitest y 13 Playwright/axe. Incluye Modulith/PostgreSQL real, checksum/rollback Flyway, JAR y 39 HTTP de contratos con validación/errores/canarios seguros. AJV valida las 51 respuestas capturadas contra OpenAPI implementado; tipos TS/metadatos se regeneran y comprueban. Los gates rechazan fallos, omisiones, reintentos/flaky y suites ausentes.

Política temporal autorizada: **REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por presupuesto/facturación; seguimiento #79**. Presupuesto $0 y validación local completa; no reintentar ni deshabilitar workflows.

OpenAPI: sintaxis, estructura 3.1 y referencias internas, más fixtures negativos. AJV verifica respuestas HTTP reales del contrato implementado y la generación reproducible de tipos; no resuelve decisiones del borrador. Seguridad: historial y archivos actuales con Gitleaks, inventario sensible, actionlint/zizmor offline, vulnerabilidades HIGH/CRITICAL de npm (incluyendo dev) y Maven (incluyendo test) con Trivy local. Las pruebas de los gates también son obligatorias.

#25 añade siete suites y amplía el JAR: mínimo 108 pruebas backend (96 Surefire + 12 Failsafe), sin reducir las 90 anteriores. Verifica JSON por proyección, MDC en finally, 20 solicitudes HTTP concurrentes, canarios en headers/query/cuerpo/excepciones, spans reales con exporter sólo en memoria de prueba, sampling, cardinalidad acotada, timeout, PostgreSQL detenido y separación de readiness/liveness. El JAR comprueba métricas en puerto administrativo loopback separado y ausencia de exportación ante variables OTEL hostiles. Detalles y límites en [OBSERVABILITY.md](OBSERVABILITY.md); resultados en [VALIDATION-25.md](../../backend/VALIDATION-25.md).

Artifacts de siete días: JUnit backend sin propiedades/stdout/stderr, resúmenes numéricos y SHA probado/origen. No subir raw reports, `.env`, JAR, SBOM, certificados, trazas ni datos fiscales. `CI Required` exige éxito de los cuatro checks, incluso si uno falla o se omite. Si GitHub no ejecuta, reportar **REMOTE CI NOT VERIFIED**, causa comprobada y comandos locales equivalentes; no deducir billing sólo por ausencia de workflows.

#26 amplía a **135 pruebas backend (120 Surefire + 15 Failsafe)**, diecinueve suites, sin reducir mínimos anteriores: veinte casos RLS/catálogo/privilegios/pool/mutaciones deliberadas, dos de concurrencia real, dos HTTP/privacidad y tres upgrade/checksum/rollback V2. Se demuestra ausencia de predicado Java, canarios, contexto malformado/falsificado y mismo RFC DEMO en dos despachos. [Diseño y evidencia negativa](../data-model/tenant-foundation-26.md), [validación local](../../backend/VALIDATION-26.md).

Autorización de negocio/API/archivos/jobs #27–30, build de imagen de aplicación y regresión visual aprobada son futuros. La base RLS de #26 no acredita esos escenarios de producto ni completa el gate piloto.
## Datos de prueba
Ficticios, marcados DEMO; nunca datos personales, XML fiscales reales, certificados ni llaves de producción.
