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

## Identidad y sesiones #27

Mínimos backend: 161 (142 Surefire, 19 Failsafe), conservando 135 anteriores. `OidcAuthenticationTests`: 22 casos con PostgreSQL 18.6 runtime real y Keycloak 26.8.1 real; Code/PKCE/OTP, firma/JWKS, issuer/audience/expiración, state/nonce, CSRF/cookies/fixation/logout, sesiones mínimas sin tokens, membresías/RLS/MFA, bootstrap, invitaciones concurrentes/caducadas/revocadas y canarios. `IdentityMigrationIT`: upgrade V2→V3, checksum y rollback/reintento; prueba adicional del JAR sin fixtures/llaves. Los controladores DTO/telemetría de pruebas siguen aislados y no forman parte de los tests OIDC ni del JAR.

Mínimos web: 37 Vitest, 13 Playwright previos y 5 de identidad reales con Chromium/HTTPS/Keycloak/PostgreSQL. `node tools/ci/auth-browser.mjs` compila el harness sólo de tests, mantiene Ryuk y cierra recursos mediante comando interno al JVM; no depende de matar un shim Java de Windows. Valida también ocho schemas sobre respuestas HTTP reales en memoria, sin persistir tokens de invitación/CSRF como evidencia. Capturas sólo del producto, nunca pantallas de credenciales; trace/video/screenshot automático de autenticación deshabilitados para no retener cookies.

[Comandos/resultados/entorno](../../backend/VALIDATION-27.md), [arquitectura y límites](../security/AUTHENTICATION.md), [capturas](../ux/evidence/issue-27/README.md). Ningún resultado antiguo sustituye estas suites nuevas. REMOTE CI NOT VERIFIED; seguimiento #79 y presupuesto $0.

## Autorización por recurso #28

Mínimo **202 backend (180 Surefire + 22 Failsafe)**, sin reducir las 161 anteriores: 36 ResourceAuthorizationTests, tres AuthorizationMigrationIT, una guarda de arquitectura y una prueba de logging adicional. PostgreSQL 18.6/runtime + Keycloak real/HTTPS; sin mocks de autenticación. [Diseño y alcance de permisos](../security/RESOURCE-AUTHORIZATION.md), [comandos/evidencia](../../backend/VALIDATION-28.md).

Casos: 401 anónimo/expirado/deshabilitado; membresías suspendidas/revocadas; cross-tenant y same-tenant sin asignación; lectura/edición/exportación/descarga separadas; supervisor/owner sin grants fiscales, auxiliar administrativo, rol desconocido/spoof; UUID conocido/adivinado; list/count/page sin filtros; revocaciones concurrentes de membresía/política/asignación con bloqueos observados; operación que retiene autoridad hasta commit; pool/sesiones multitenant; SQL directo, MFA, mensajes/logs seguros; estado/versionado/rollback, lote parcialmente autorizado, contexto retenido/anidado y FK compuesta. Oráculo real falla deliberadamente al quitar resource_fence y vuelve a pasar al restaurarla; guardas detectan drift FORCE/grants. V4 verifica upgrade V3, checksums previos, instalación una vez, checksum alterado y rollback/reintento.

Los fixtures no son tablas fiscales ni autorizan una matriz de producto. Las pruebas previas conservan firma/issuer/audience/state/nonce/PKCE manipulados, invitaciones y MFA reales. El JAR excluye los probes/SQL sintéticos nuevos. Frontend sin cambio visible: se conservan 37 Vitest, 13 Playwright de fundación y cinco OIDC reales. No se reduce ningún mínimo CI ni se publican logs crudos.
