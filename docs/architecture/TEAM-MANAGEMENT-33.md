# Equipo #33 — auditoría y fronteras de implementación

Base remota: `19d6ec043ccbfda3c75c39b3e5428a72666cd3ba`, merge de #32. Rama: `issue-33-team-management`. Estado: **invitaciones por correo local/test implementadas con aprobación limitada; administración general/cartera pendientes**. FR-005/011, BR-003/004, NFR-007. V10 es aditiva; V1–V9 y los grants administrativos permanecen intactos. [Guía del corte implementado](../security/LOCAL-EMAIL-INVITATIONS-33.md).

## Infraestructura comprobada y reutilizable

| Base integrada | Implementación efectiva | Reutilización en #33 |
|---|---|---|
| #27 / V3 | OIDC Code+PKCE, AppPrincipal mínimo, sesiones JDBC; identidades globales por issuer/subject; memberships/roles; invitación de 24h con hash, receptor exacto, revocación y consumo atómico | Extender identity, sin segundo sistema de usuarios/invitaciones ni tokens en sesiones |
| #28 / V4 | AuthorizedTenantOperations, capacidad/recurso, contexto HMAC/xid y RLS; runtime sólo lee membresías propias directamente | Directorio de equipo mediante funciones estrechas autorizadas; no SELECT global ni elevación por claims/UI |
| #29 / V5 | Journal append-only, atribución verificada, triggers de invitación/estado y outbox en la misma transacción | Incorporar cambios de rol al catálogo tipado; no usar logs como historial ni mensajes con correo/tokens |
| #30 | Harness negativo, mutaciones, concurrencia/pool y verificación del JAR | Preservar suites y mínimos; añadir casos reales del nuevo módulo, no reutilizar resultados históricos |
| #31 / V6–V7 | Raíz ACTIVE/SUSPENDED, orden de locks y perfil; permiso owner+MFA limitado a settings | Estado y contexto del despacho; no derivar permisos de equipo del permiso de configuración |
| #32 / V8–V9 | Cuotas privadas, reservas, trigger productivo de activaciones y consulta owner+MFA | Conservar COUNT ACTIVE, incluso identidad global DISABLED; invitaciones pendientes no consumen; reactivación revalida cuota |
| #52 | AppShell, workspace, PageHeader, Field, DataTable, filtros, confirmaciones y estados | Componer vista Equipo navy/teal, sin otro tema ni controles administrativos ficticios |

`IdentityStore` ya ofrece invitar, revocar invitación, suspender/revocar miembro y aceptar token. El controlador existente usa `/api/v1/auth/...`. V10 añade el adaptador de correo verificado y el formulario Equipo, reutilizando esos comandos. No existe listado autorizado de todo el equipo/invitaciones, cambio de rol ni endpoint de reactivación; no confundir los fixtures administrativos de cuota con esa autorización HTTP.

V9 protege aceptación y cualquier INSERT/UPDATE ACTIVE, pero no concede una facultad pública de reactivar. Sin cuota vigente, las activaciones ordinarias fallan. La excepción de primer owner es interna, exacta y no debe reutilizarse. La auditoría de reactivación ya existe en el trigger; un nuevo comando debe participar en la misma transacción, sin duplicar eventos o consumo.

## Decisiones necesarias

Ver [propuesta limitada con matriz y alternativas](../security/TEAM-POLICY-PROPOSAL-33.md). MEMBERSHIP_VIEW, MEMBERSHIP_ASSIGN_ROLE y reactivación no tienen grant aprobado en main. OQ-014 no autoriza todavía invitaciones productivas por email. El catálogo de actores sigue Proposed; no se toma como autorización universal del owner/supervisor.

Datos reales disponibles: IDs de membresía, roles, estado y timestamps persistidos. La aprobación limitada B permite nombre opcional/correo privados de una identidad verificada; V10 los mantiene separados de AppPrincipal/sesiones y no concede su lectura general. No existe fuente específica de última actividad del colaborador. No se muestra nombre, correo, última actividad o cartera inventados; no hay autolink de usuarios por email.

La entrega externa por correo no está configurada. Creación y entrega son estados distintos: no hay correo enviado por el mero INSERT. Sólo se conserva hash del token; una eventual entrega manual debe mostrarse explícitamente y retirar el secreto antes de capturar evidencia.

## Contrato futuro de cartera — no API productiva

No hay tabla productiva de contribuyentes/clientes aprobada. V4 tiene infraestructura genérica de asignaciones, pero sus expedientes concretos son fixtures desechables. #74 y OQ-013/015 bloquean derivar una tabla fiscal o decidir cardinalidad de responsables.

Contrato propuesto para el módulo correspondiente, sin implementarlo en #33:

1. Actor de sesión vigente → identidad/membresía ACTIVE → capacidad TAXPAYER_ASSIGN expresamente aprobada → contribuyente autorizado dentro del mismo tenant → colaborador destino del mismo tenant, activo y elegible.
2. Request sólo con candidatos de recurso/membresía, expectedVersion e Idempotency-Key; no actor, roles, permit ni tenant de confianza aportados por UI.
3. Relación física con tenant_id y FK compuestas a contribuyente y membresía; cardinalidad/vigencia tras la decisión OQ-013. No usar RFC como PK o alcance.
4. Cambio, asignación/revocación, journal y outbox atómicos; bloqueo/versionado contra cambios concurrentes. Una revocación confirmada debe retirar acceso en la siguiente operación, sin permisos reutilizables fuera del callback.
5. Aplicar tenant y resource fence antes de devolver filas, conteos, páginas, descargas o reportes. Nunca filtrar una cartera amplia sólo en React.
6. Pruebas de dos contadores con carteras distintas, actor multidespacho, UUID adivinado, búsqueda/listado sin filtro, revocación concurrente y eliminación controlada de cercas en PostgreSQL desechable.

No se expondrá un botón de asignar cartera que parezca operativo ni una lista fiscal DEMO dentro de una vista de datos reales.

## Validación prevista y discrepancias

Los positivos de equipo deberán usar migraciones y permisos aprobados reales. No se instalarán grants sintéticos para presentar el módulo como productivo. E2E con PostgreSQL18.6/Keycloak y pruebas SQL con rol runtime; identidad ajena, otros tenants/roles, MFA, propietario/autoedición, cuota agotada, concurrencia, versión, idempotencia, rollback y ambos sinks. Frontend: estados y limpieza de contexto, responsive/teclado, capturas reales 1440×900/390×844. Los resultados se publicarán sólo cuando exista implementación y se ejecuten sobre su SHA final.

Hay referencias históricas desactualizadas en README/documentación (por ejemplo, #79 bloqueado y #31 pendiente) aunque main ya contiene #31–32 y aprobaciones parciales V7/V9. Prevalecen el código, las decisiones registradas y los resultados de cada SHA; esa documentación histórica no amplía autoridad ni acredita CI de #33.

Este documento no convierte pruebas futuras en PASS ni cierra decisiones. El propietario aprobó expresamente B para Keycloak local/test; sus respuestas no aprobaron A. Se implementa y prueba ese corte funcional, sin grants sintéticos de administración general. Los resultados exactos del SHA final se publican en el PR.

Línea base efectiva que deberá preservarse al implementar: **385 Java (347 Surefire+38 Failsafe),92 Vitest,61 Playwright de fundación/presentación,15 OIDC y19 tools/ci**, según los mínimos del verificador y VALIDATION-32. Los nuevos casos aumentarán esos mínimos; los resultados de #32 no acreditan #33. Backend CI, Web CI, OpenAPI CI, Security CI y CI Required se verificarán por el nuevo SHA, sin cambios de presupuesto, billing o protecciones.
