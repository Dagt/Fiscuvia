**C03 #36 — alcance efectivo de esta rama:** [ficha real y límites](docs/ux/TAXPAYER-PROFILE-36.md), [decisiones propuestas D36-01..03](docs/decisions/issue-36-approval-proposal.md), [validación y criterios](docs/delivery/VALIDATION-36.md). Base main `05f1d0b58783438e193ae9ad86e7181b0e62c135` (PR #94). Perfil C01 y comandos existentes integrados; contactos/certificados/actividad/responsables pendientes de autorización. Sin DDL ni permisos nuevos; V1–V13 intactas. Esta nota prevalece sobre estados históricos siguientes.

**Entitlements técnicos #32:** [modelo y fronteras de aprobación](docs/architecture/TENANT-ENTITLEMENTS.md). V8 incorpora límites versionados, reservas, medición real de membresías y consulta condicionada a TENANT_PLAN_VIEW; sin grants implícitos, precios ni cobros. V9 instala el permiso owner+MFA y el trigger de activaciones según la aprobación limitada del propietario.

**C02 #35 — directorio en esta rama:** `/app/taxpayers` integra consulta, búsqueda y paginación de servidor, alta, perfil administrativo, edición, archivado y restauración sobre C01 real. Base main `518bc327173c355b2e6f843442f74248b980ff5c` (PR #93). [Flujos/seguridad](docs/ux/TAXPAYER-DIRECTORY-35.md), [extensión de lectura](docs/api/taxpayer-directory-35.md), [validación](docs/delivery/VALIDATION-35.md). V13 sólo funciones de presentación; V1–V12 intactas, sin nuevos grants. Responsable/ficha 360° siguen en #36. PR Draft para revisión, sin merge. Las notas anteriores son históricas.

**C01 #34 — implementación en esta rama:** [modelo físico, API y permisos](docs/architecture/TAXPAYER-DOMAIN-34.md) conforme a [D74-01..05](docs/decisions/issue-74-owner-approval.md), después del merge humano de #92. Base main `4c748a833a1ca2f0deef5c6fbd7702c0f66996cd`; V12 aditiva, V1–V11 intactas. Altas/perfil/consulta/archivo/restauración, revisión de grants versionada, cuotas reales y journal/outbox. [Validación](docs/delivery/VALIDATION-34.md); UI #35 y cartera/revisión #36 siguen pendientes. Las notas de entregas anteriores son históricas; presupuesto $0 y CI por SHA.

**SPEC01 #74 — aprobación limitada 2026-11-12:** [D74-01..05, fuente y restricciones](docs/decisions/issue-74-owner-approval.md) autorizan la especificación inicial de #34 y difieren decisiones fiscales posteriores. [Modelo/esquema/contrato](docs/architecture/SPEC01-fiscal-domain-74.md), con habilitación de grants ligada a versión de membresía: reactivar/cambiar rol no recupera acceso histórico sin revisión explícita. Base main `05dc33e77e290777c316d26edabb7ce1e7b28c44` (#91), V1–V11 intactas; #92 sólo documental, para revisión final/merge humano, sin comenzar #34. Las notas de entregas anteriores son históricas; CI por SHA nuevo y presupuesto $0.

**Equipo #33:** [administración aprobada owner+MFA](docs/security/TEAM-ADMINISTRATION-33.md): directorio e invitaciones paginados, cambios de rol operativo, suspensión/revocación y reactivación con cuotas, versiones, idempotencia y auditoría/outbox. V11 conserva V1–V10. [Correo B](docs/security/LOCAL-EMAIL-INVITATIONS-33.md) sólo Keycloak local/test, token de un uso y entrega manual. Cartera permanece #74; producción/correo/matriz fiscal conservan pendientes.

**Despachos #31:** [provisioning interno, configuración y límites de autoridad](docs/architecture/TENANT-PROVISIONING.md). Nuevo módulo tenants, perfiles privados, lifecycle administrativo no destructivo, configuración autorizada y nombres reales en AppShell. Sin operador HTTP, alta pública, cobros o almacenamiento de logos. La habilitación permanente de TENANT_SETTINGS_MANAGE requiere aprobación expresa de la política mínima documentada; permanece DENY mientras esté pendiente. No se adelantan #32/#33 ni el gate fiscal #74.

**Base visual #52:** [design system y AppShell implementados](docs/ux/DESIGN-SYSTEM-52.md), catálogo `/desarrollo/componentes` y [evidencia real](docs/ux/evidence/issue-52/README.md). Referencia escrita aprobada por Dagt el 2026-11-03 (opción B OQ-021); el collage continúa ausente. No implementa provisioning #31 ni pixel-diff #56.

**Todo tu despacho, una sola vía.**

SaaS B2B para la gestión operativa de despachos contables en México: contribuyentes, obligaciones, calendario, responsables, documentos y acuses, honorarios, reportes y control de cumplimiento **registrado por el despacho**. CFDI y conexión directa SAT son fases posteriores, sujetas a viabilidad/seguridad; no reemplaza CONTPAQi Contabilidad ni un PAC.

> **Estado al 2026-11-02:** base #20–29 integrada en main `f40a4980c1eaf268c16fcc39b2855dbcc71378a6` (#85). Esta rama añade [regresión transversal #30](docs/security/CROSS-TENANT-REGRESSION.md) sobre identidad, RLS, autorización y journal/outbox. #79 cerrado tras CI Ubuntu exitosa de #85; cada PR se verifica por SHA, presupuesto $0 intacto. Sin proveedor productivo, matriz fiscal aprobada ni tablas fiscales; #74 sigue vigente. Documentos Proposed conservan su estado.

## Web local — issue #21

Node 24.13.1 / npm 11.8.0. Desde `web/`:

```powershell
npm ci
npm run dev
```

Bienvenida y estados demostrativos con identidad Fiscuvia, navegación responsive y áreas futuras deshabilitadas. La base visual está en `/desarrollo` y no requiere backend. El acceso real en `/login` sí requiere BFF/OIDC local; no hay autenticación simulada. Consulta [web/README.md](web/README.md) para arquitectura, versiones y comandos de validación; [capturas y evidencia real](docs/ux/evidence/issue-21/README.md). #52 y #56 conservan sus alcances independientes.

## Backend y PostgreSQL local — issues #20/#22

Requiere JDK 25 y Docker con contenedores Linux. Maven se descarga mediante el wrapper. Desde la raíz, generar credenciales sintéticas locales y arrancar PostgreSQL:

```powershell
pwsh -File infra/New-LocalEnvironment.ps1
docker compose --env-file infra/.env -f infra/compose.yaml up -d --wait
```

POSIX: `sh infra/new-local-env.sh` y el mismo comando Compose. Los generadores no sobrescriben `.env`. Ver [guía PostgreSQL, permisos, variables y limpieza](infra/README.md).

```powershell
cd backend
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd spring-boot:run
```

En POSIX: `./mvnw -B -ntp clean verify` y `./mvnw spring-boot:run`.
Salud: `http://127.0.0.1:8080/actuator/health`. La aplicación escucha en loopback, admite sólo local/test y requiere PostgreSQL. Los tests crean DBs efímeras con Testcontainers, sin necesitar Compose ni `.env`. No hay fallback H2, tablas fiscales ni endpoints de negocio. Tenancy/RLS #26 se documenta en la guía física enlazada abajo.
Consulta [backend/README.md](backend/README.md) y [evidencia #22](backend/VALIDATION-22.md). Seguridad por despacho en #26–30; gate #74 antes del esquema fiscal.

Observabilidad: [atributos permitidos, tracing sin exportación, métricas y runbook](docs/delivery/OBSERVABILITY.md). Actuator conserva exposición mínima; diagnóstico de métricas sólo con opt-in local en puerto loopback separado. [Evidencia #25](backend/VALIDATION-25.md).

Tenancy/RLS #26: [modelo físico, contexto transaccional, permisos y límites](docs/data-model/tenant-foundation-26.md); [evidencia local](backend/VALIDATION-26.md). La selección de despacho en #27 valida membresía real antes del contexto; capacidades/expedientes siguen en #28. [Autenticación local y configuración](docs/security/AUTHENTICATION.md).

## Integración continua — issue #23

El workflow [CI](.github/workflows/ci.yml) valida PRs a main, incluso Draft, push a main y ejecución manual. Checks estables: `Backend CI`, `Web CI`, `OpenAPI CI`, `Security CI` y `CI Required`. No despliega ni requiere secrets. Ver [guía CI, comandos equivalentes, artifacts, diagnóstico y protección recomendada](docs/delivery/CI.md). El resultado debe consultarse por SHA en GitHub Actions; la existencia del YAML no acredita una ejecución exitosa.

**Estado CI por SHA:** #79 quedó resuelto tras ejecuciones reales de los cinco checks. Cada PR necesita su propia evidencia local/remota; los resultados históricos no acreditan el HEAD nuevo. Presupuesto $0, billing, protecciones y verificaciones se mantienen sin cambios.

## Contratos API — issue #24

`GET /api/v1/public/info` devuelve sólo marca/eslogan/versión. Errores RFC 9457 seguros, validación servidor y cliente TypeScript con validación runtime, sin nuevas pantallas ni solicitudes automáticas. [Contrato implementado](docs/api/openapi-v1.yaml) separado del [borrador propuesto](docs/api/openapi-v1-draft.yaml); [guía #24 y comandos](docs/api/foundation-24.md). `/api/v1/me` y OIDC local/test están implementados en #27; el dominio fiscal sigue futuro. #29 añade deduplicación de comandos internos sin prometer replay HTTP de tokens.

## Para Codex y desarrolladores
1. [AGENTS.md](AGENTS.md): reglas obligatorias, documentación fuente de verdad y PR Draft sin merge.
2. [Mapa completo de documentación](docs/README.md): descubrimiento, producto, dominio, estados, datos, contratos, ADR, UX y backlog.
3. [Catálogo FR-###](docs/product/requirements-catalog.md), [BR-###](docs/product/business-rules-catalog.md), [NFR-###](docs/architecture/non-functional-requirements.md), [OQ-###](docs/product/open-questions.md).
4. [MVP](docs/product/MVP.md) y [modelo de dominio](docs/domain/domain-model.md).
5. [Decisiones ADR-###](docs/decisions/README.md), [esquema PostgreSQL v0.1](docs/data-model/schema-v0.1.md), [API](docs/api/README.md).
6. [Sistema visual](docs/ux/DESIGN-SYSTEM.md), [pantallas](docs/ux/screen-inventory.md) y [criterios obligatorios](docs/ux/VISUAL-ACCEPTANCE.md).
7. [Backlog](docs/backlog/README.md), [oleadas](docs/backlog/implementation-order.md), [protocolo Codex](docs/backlog/codex-workflow.md), [DoD](docs/delivery/DEFINITION-OF-DONE.md).

## GitHub
- [PR #1 — documentación fundacional](https://github.com/Dagt/Fiscuvia/pull/1) — integrado en `ce561482fda7889b005deaad3d822395b89a82df`.
- [PR #75 — scaffold backend #20](https://github.com/Dagt/Fiscuvia/pull/75) — integrado mediante squash en `1d0590a`.
- [PR #76 — scaffold web #21](https://github.com/Dagt/Fiscuvia/pull/76) — integrado mediante squash en `6ac5dd5d3750b76e50494953c10724b25368bad3`.
- [PR #77 — PostgreSQL/Docker/Flyway #22](https://github.com/Dagt/Fiscuvia/pull/77) — integrado mediante squash en `bf61a9697e606cfd6b709a09ed2997947c8c268f`.
- [PR #78 — CI #23](https://github.com/Dagt/Fiscuvia/pull/78) — integrado mediante squash en `edbc6ddc1a54e17e5cd38dd108af67cc5eb7e1c7`; verificación remota pendiente #79.
- [Issue #73 — seguimiento MVP](https://github.com/Dagt/Fiscuvia/issues/73) — épicas, dependencias y gates.
- [Issue #74 — revisión del modelo fiscal/esquema antes de migraciones](https://github.com/Dagt/Fiscuvia/issues/74).

## Decisiones de dirección vigentes
Nombre Fiscuvia, eslogan y SaaS multi-tenant con Java/Spring Boot + React/TS/PostgreSQL. Las versiones comprobadas del scaffold se documentan en `backend/README.md`; no implican aceptación global de ADR. Proveedor OIDC, reglas fiscales y precios permanecen Proposed o Open hasta validación.

## Calidad UX
La referencia aprobada es una composición conceptual de seis pantallas, con sidebar navy, acento teal y estilo limpio/premium. Todavía debe incorporarse su imagen fuente al repositorio. Cada PR frontend necesitará screenshots **reales**, accesibilidad y revisión comparativa; no se acepta un frontend genérico.

## Seguridad
Datos sintéticos hasta gates de privacidad/seguridad. No subir llaves e.firma, contraseñas SAT, RFC/CFDI reales, tokens ni información fiscal de clientes. No afirmar certificación SAT ni estados oficiales sin fuente.

## Journal y outbox — issue #29

[Journal, catálogo y privacidad](docs/security/AUDIT-JOURNAL.md), [outbox y diagnóstico](docs/delivery/OUTBOX.md), [validación reproducible](backend/VALIDATION-29.md). Cambios sensibles de identidad, evento y outbox se confirman juntos; rollback/fallo de auditoría revierte todo. No hay lector público, worker automático ni autoridad de negocio a partir del tenant_id de una fila.
