# Validación #32 — entitlements técnicos

Base main verificada: `1d0105e12d62aaed350d09275704d5ecbb4be4a6`. Rama `issue-32-tenant-plans-quotas-entitlements`. SHA final, URL del Draft y resultados de GitHub Actions se registran en el PR para evitar autorreferencias al commit. Sin merge, cierre, despliegue ni avance a #33.

## Alcance comprobable

[Arquitectura y política pendiente](../docs/architecture/TENANT-ENTITLEMENTS.md). V8, módulo entitlements, configuración administrativa interna, reservas serializadas, medición real COUNT de membresías, consulta sin grants implícitos, UI preparada y [14 capturas](../docs/ux/evidence/issue-32/README.md). V1–V7 sin cambios. No existen consumos reales de contribuyentes/storage/jobs, CRUD fiscal, workers nuevos ni precios.

**Pendiente de aprobación:** TENANT_PLAN_VIEW limitado y conteo/aceptación/reactivación de membresías. La implementación productiva conserva DENY para consulta y no instala el trigger de aceptación; las pruebas de esa política/hook son fixtures efímeros. No se declara completa la integración productiva de invitaciones ni la matriz comercial. OQ-007/014/015, ADR-015 y #74 siguen pendientes.

## Entorno y comandos

Linux, Java Temurin 25.0.5+8, wrapper Maven 3.9.16, Node 24.22.1/npm 11.9.0 (dentro del rango del proyecto), Docker rootless 29.9.2, PostgreSQL 18.6 y Keycloak 26.8.1 fijados por digest. Credenciales sintéticas efímeras, sin configuración productiva.

Maven local requiere proxy de la sesión mediante settings fuera de Git y truststore del sistema con la CA suministrada. No se deshabilita TLS. El umask inicial 0077 impedía al usuario PostgreSQL leer el script sintético copiado: se usa umask 0022 y permisos de lectura sólo en archivos fuente públicos. Logs/handoff/configuración privados mantienen su protección. Sin modificar V1–V7 ni los scripts del provisioning previo.

Se ejecutan los comandos equivalentes del workflow:

- backend: `./mvnw -B -ntp clean verify`, guardas Modulith, Flyway/upgrade/rollback/checksum, RLS, OIDC/MFA, journal/outbox y JAR real; resultado final y cualquier reejecución se detallan abajo/PR.
- web: `npm ci`, typecheck, ESLint, Prettier, Vitest, build y Playwright completo. **91 Vitest y 61 Playwright** correctos; se conservan 85/33 previos. Escenarios de presentación usan sólo interceptores de tests, nunca como evidencia sustitutiva de seguridad.
- herramientas: **19 tests correctos** (18 anteriores conservados), validación OpenAPI implementado/borrador y comparación de tipos regenerados.
- seguridad: Gitleaks de historia/fuentes sin leaks, actionlint y zizmor sin findings; Trivy npm con dev y Maven con test, cero HIGH/CRITICAL. SBOM CycloneDX 2.9.3 con includeTestScope=true, sólo en target ignorado.
- capturas: 14 Chromium a 1440×900 /390×844, manifest con hashes de fuente/PNG; axe y overflow comprobados. No pantallas de credenciales ni datos reales.
- `git diff --check`; allowlist de artifacts, sin subir reports crudos, cookies, certificados, classpath, JAR o SBOM.

Trivy necesitó la CA del proxy montada read-only y caché/temporales en memoria por el disco reducido del entorno. Se mantuvieron cap-drop=ALL, no-new-privileges, usuario de la caché, lectura de fuentes y los mismos scanners/gates/dependencias. Un fallo inicial de descarga por CA y otro por espacio no se presentaron como éxito; la ejecución completa posterior sí examinó npm y Maven.

Una ejecución de clean verify encontró un fallo de infraestructura de Docker rootless: al publicar PostgreSQL, RootlessKit recibió `bind: address already in use` para un puerto efímero. No fue un fallo de aserción ni se deshabilitó la suite. La repetición `verify -Dtest=PostgresFoundationTests` ejecutó correctamente sus tres pruebas; los XML de las restantes suites se conservan del clean verify anterior. Las 328 pruebas Surefire quedan correctas, sin omisiones; ese comando continúa las suites Failsafe completas. La continuación terminó BUILD SUCCESS: 35 Failsafe correctas, incluido el arranque real del JAR, y 363 Java en el conjunto de XML conservados (328+35). El verificador de reports confirma todas las suites sin fallos ni omisiones; allowlist backend correcta. El PR registra el clean verify remoto del SHA final.

## Matriz y límites

EntitlementTests agrega 21 casos con OIDC/PostgreSQL reales y roles separados: consulta denegada por defecto, fixtures limitados de lectura, A/B y UUID falso, anónimo, medición/recursos futuros, ausencia de configuración, versiones/replay, cambio concurrente, valores inválidos/overflow, reducción no destructiva, FORCE/grants, identidad desactivada, hook preparado con cupo exacto/suspendido, rollback de sink, reservas/replay/liberación doble, rollback/reintento, confirmación sólo con membresía real, reducción durante reserva y dos reservas para última unidad. Los tests del motor que siembran contextos privados son fixtures administrativos confiables, no filtros HTTP ni mocks de OIDC. La seguridad HTTP y la negativa SQL runtime se comprueban por separado.

EntitlementMigrationIT añade tres casos de V7→V8: raíz anterior y checksums conservados, instalación única, checksum alterado y DDL fallido/rollback/reintento. Resultado local y mínimo exigido: **363 Java (328 Surefire +35 Failsafe)**, conservando las 339 anteriores. Los gates exigen suites nuevas sin reducir mínimos. OIDC navegador: **13/13 correctos**, con Keycloak/BFF/PostgreSQL reales, sin mocks ni omisiones. El verificador web confirma 91 Vitest +61 Playwright +13 OIDC; allowlist web correcta.

Las aceptaciones simultáneas con cuota, reactivación productiva y política de invitaciones pendientes requieren aprobar la política antes de instalar el hook y añadir su gate de integración completo. Los contratos de futuros módulos no acreditan almacenamiento ni jobs existentes. Las pruebas anteriores conservan revocación/suspensión, MFA y sesiones reales; no se marca resuelta OQ-014/OQ-015.

## GitHub Actions

Se requieren Backend CI, Web CI, OpenAPI CI, Security CI y CI Required sobre el SHA final. Consultar resultado/enlace del run en el Draft; no reutilizar checks de #89 o runs históricos. El estado remoto vigente se registra en el PR por SHA y URL del run: sólo se declara verificado después de observar los cinco checks correctos de ese SHA. La evidencia local de este documento corresponde al working tree de esta entrega, no al código histórico del SHA base. No se cambian pipelines, gasto, protecciones ni visibilidad.

Evidencia remota vigente y SHA final: [PR Draft #90](https://github.com/Dagt/Fiscuvia/pull/90). La descripción registra cada check y enlaza el run correspondiente; no se sustituye esa comprobación con el estado histórico del commit base.
