# Fiscuvia backend — plataforma, tenancy, identidad y autorización #20–29

## Invitaciones locales #33

[V10, API, identidad verificada y privacidad](../docs/security/LOCAL-EMAIL-INVITATIONS-33.md). Adaptador de correo sobre el flujo original de invitaciones, sin permisos nuevos de administración de equipo. Opt-in FISCUVIA_OIDC_CONTACT_POLICY=keycloak-local-verified-email-v1 sólo con Keycloak local/test; disabled por defecto. No hay entrega externa. Contactos privados con prueba HMAC por propósito; token mostrado una vez, sólo hash almacenado y replay sin secreto. V1–V9 intactas; nueva suite27 HTTP/OIDC/SQL +3 migration IT, sin grants sintéticos positivos.

## Despachos #31

[Guía implementada de provisioning, SQL, configuración y operación local](../docs/architecture/TENANT-PROVISIONING.md). V6 añade perfiles/recibos privados, procedimientos administrativos específicos y GET/POST de settings bajo AuthorizedTenantOperations. V7 instala la política limitada de edición owner+MFA expresamente aprobada; V1–V6 permanecen intactas. Tests/harness consumen esa política real sin sembrar grants de edición. `/me` entrega nombres/estados propios; suspensión bloquea acceso operativo. No hay endpoint de operador, logo upload o capacidades fiscales. No se añaden dependencias ni proveedores.

Pruebas específicas: `./mvnw -B -ntp -Dtest=TenantProvisioningTests -Dit.test=ProvisioningMigrationIT verify` (PowerShell: `./mvnw.cmd -B -ntp '-Dtest=TenantProvisioningTests' '-Dit.test=ProvisioningMigrationIT' verify`). No reemplaza `clean verify` completo. PostgreSQL18.6, Keycloak real y Docker son obligatorios; no hay skip sin Docker. El verificador CI preserva 295 pruebas anteriores y exige las suites nuevas. Guardas comprueban privilegios/FORCE del directorio; las mutaciones sólo ocurren en contenedores desechables.

Backend ejecutable local/test con PostgreSQL real y Flyway. Base #20–26 integrada con raíz mínima de despachos y RLS. Sin tablas fiscales ni endpoints de negocio. Gate #74 y matriz funcional/OQ-015 siguen pendientes. #27 está integrado; #28 añade [capacidad/recurso y transacciones autorizadas](../docs/security/RESOURCE-AUTHORIZATION.md), V4 y pruebas negativas. No se otorgan permisos fiscales todavía.

PR #82/#26 está integrado en main `05b5b18b157cd88d8f76967f9d2d3b179960f7aa`. #27 añade Spring Security 7.1.2, Spring Session JDBC 4.1.1, V3 y Keycloak 26.8.1 exclusivamente local/test. [Guía efectiva de autenticación, configuración, SQL y límites](../docs/security/AUTHENTICATION.md); [validación local](VALIDATION-27.md). La propuesta inicial se conserva como antecedente; no se aprueba proveedor de producción.

## Contratos #24 (integrados)

`GET /api/v1/public/info` devuelve únicamente producto/eslogan/versión. Validación Jakarta servidor, UUID/binding estricto, RFC 9457/códigos/correlación seguros y primitivas reutilizables, sin JPA ni autenticación. [Guía y ejemplos](../docs/api/foundation-24.md), [contrato implementado](../docs/api/openapi-v1.yaml). Dependencia nueva: starter-validation 4.1.2 → Hibernate Validator 9.1.5.Final / Jakarta Validation 3.1.1 administrados por Boot.

`clean verify` exige **250** pruebas (225 Surefire + 25 Failsafe), conserva las 203 anteriores y prueba HTTP real con controladores sólo de tests. El JAR confirma info pública y ausencia de fixtures; logs/respuestas no contienen canarios secretos. Después ejecutar desde la raíz `node tools/ci/contracts.mjs --responses backend/target/contract-responses.json` para comprobar las 51 respuestas frente al esquema. Requiere `npm ci --prefix tools/ci --ignore-scripts`. Esas respuestas no se suben como artifacts. [Observabilidad #25](../docs/delivery/OBSERVABILITY.md); [tenancy física, convenciones transaccionales y riesgos #26](../docs/data-model/tenant-foundation-26.md); [evidencia #26](VALIDATION-26.md).

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79.** Presupuesto $0; no se deshabilitan workflows ni reintentan runners. No se implementa #30 ni reglas fiscales de #74; #27 se limita a identidad local/test.

## Tenancy transaccional #26

V2 añade `app.tenants(tenant_id,created_at)`, un registro privado tenant/xid y dos funciones con permisos explícitos. ENABLE/FORCE RLS y una cerca restrictiva USING/WITH CHECK; runtime sólo lee la raíz, no aprovisiona despachos. Guardas rechazan ownership, membresías/privilegios DB y protecciones/grants inseguros. V1 permanece intacta; Flyway aplica V2 sobre DB nueva o V1 existente.

El puerto `platform.TenantTransactions.execute(authorizedTenantId, work)` establece contexto local a una transacción JDBC real y participa en una exterior REQUIRED. Mismo tenant puede anidar; cambiarlo provoca rechazo/rollback. NESTED/savepoints y transacciones exteriores read-only se rechazan. No aceptar UUID de HTTP como autorización, crear conexiones independientes ni emitir SET de sesión/control transaccional manual. `identity.AuthorizedTenantOperations` autoriza identidad/membresía antes del puerto; #28 exige capacidad/recurso explícitos. `/auth/tenant` sólo valida acceso; no publica un setter genérico de contexto.

Las pruebas RLS usan runtime y fixtures desechables fuera de migraciones productivas: dos despachos con mismo RFC DEMO, CRUD/FK/constraints, pool tamaño 1 con PID idéntico, cuatro conexiones simultáneas y resistencia frente a políticas eliminadas/relajadas. Los fixtures se siembran con migrador y contexto respetando FORCE; no se usan bypasses para los datos. Comando focalizado: `.\mvnw.cmd -B -ntp '-Dtest=TenantRlsTests,TenantConcurrencyTests,TenantHttpPrivacyTests' test` (POSIX `./mvnw`). Las tres pruebas `TenantMigrationIT` se ejecutan en verify.

## Herramientas y compatibilidad

| Componente | Versión fijada / comprobada |
|---|---|
| JDK | Java 25; ejecución local Oracle JDK 25.0.3+9-LTS-195 |
| Spring Boot | 4.1.2 |
| Spring Framework | 7.0.10, gestionado por Boot |
| Spring Modulith | 2.1.2, BOM explícito |
| Maven / Wrapper | 3.9.16 / 3.3.4, `only-script` |
| JUnit Jupiter | 6.0.3, gestionado por Boot |
| ArchUnit | 1.4.2, transitivo de Modulith |
| Compiler / Surefire / Failsafe | 3.15.0 / 3.5.6 / 3.5.6, gestionados por Boot |
| Maven Enforcer | 3.6.2 |
| PostgreSQL / imagen | 18.6 / 18.6-alpine3.24, digest fijado en Compose y tests |
| Flyway / JDBC PostgreSQL | 12.4.0 / 42.7.14, gestionados por Boot |
| Testcontainers | 2.0.5, gestionado por Boot |

Java 25 es compatible según los [requisitos oficiales de Boot 4.1](https://docs.spring.io/spring-boot/4.1/system-requirements.html). La compatibilidad efectiva se comprueba resolviendo dependencias, compilando con `release 25`, verificando módulos y ejecutando el JAR durante `verify`. No fue necesario activar el fallback Java 21 de ADR-002. Enforcer exige JDK 25 y Maven 3.9.16 para evitar variaciones accidentales.

El propietario autorizó JUnit 6/Jupiter con Boot 4.1.2 y Modulith 2.1.2 el 2026-10-29 en esta conversación de implementación. Sustituye el requisito inicial de JUnit 5: Boot 3.5.16 pasó la exploración inicial, pero fue la [última publicación OSS de la línea 3.5](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/). La [guía de Boot 4](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) documenta el cambio a JUnit 6. Los ADR-001/002/003 conservan su estado Proposed; esta selección no aprueba las decisiones de dominio ni resuelve globalmente OQ-022.

El parent de Boot y el BOM de Modulith fijan las dependencias transitivas y plugins del lifecycle; no se usan versiones dinámicas ni dependencias snapshot. El timestamp del archivo está fijado en el POM para reproducibilidad. Esto no garantiza bytes idénticos entre distintos JDK/proveedores o sistemas operativos.

## Instalación y ejecución

Instalar JDK 25 y Docker con contenedores Linux. No es necesario instalar Maven. La primera ejecución necesita HTTPS a Maven Central y registros de imágenes; posteriores builds usan cachés. Para desarrollar, seguir primero la [guía PostgreSQL/Compose](../infra/README.md): generar `infra/.env` y ejecutar `docker compose --env-file infra/.env -f infra/compose.yaml up -d --wait` desde la raíz. Los tests se autoaprovisionan con Testcontainers y no requieren ese Compose ni `.env`.

Desde `backend/`, en PowerShell:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd spring-boot:run
```

En una shell POSIX:

```sh
./mvnw --version
./mvnw -B -ntp clean verify
./mvnw spring-boot:run
```

También se puede arrancar el artefacto compilado:

```powershell
java -jar target/fiscuvia-backend-0.0.1-SNAPSHOT.jar
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

Detener con Ctrl+C. Health ahora incluye conectividad PostgreSQL; readiness incluye `readinessState,db` y liveness sigue siendo del proceso. `UP` no certifica OIDC/SAT, aislamiento por despacho ni preparación para un piloto.

## Configuración y fallos de arranque

| Configuración | Valor / alcance |
|---|---|
| `spring.profiles.default` | `local`, sin variable obligatoria |
| `SPRING_PROFILES_ACTIVE` | opcional: `local`; `test` es activado por las pruebas |
| `SERVER_PORT` / `--server.port` | 8080 por defecto; 0 permite puerto efímero |
| `server.address` | 127.0.0.1 por defecto; desarrollo local en loopback |
| `application-local.yml` | inicialización inmediata para detectar errores al arrancar |
| `src/test/resources/application-test.yml` | puerto efímero y banner deshabilitado; no se empaqueta |
| `DB_PORT` | 55433 por defecto, sólo loopback y base `fiscuvia_local` |
| `DB_RUNTIME_PASSWORD` / `DB_MIGRATION_PASSWORD` | requeridas en local, sin defaults; importadas de `../infra/.env` o entorno |
| `TEST_DB_JDBC_URL` / `TEST_DB_RUNTIME_PASSWORD` / `TEST_DB_MIGRATION_PASSWORD` | sólo pruebas, suministradas por Testcontainers |

No hay secretos versionados ni issuer OIDC. La DB es obligatoria: configuración ausente, DB inaccesible o contraseñas inválidas fallan explícitamente, sin H2. Un guard previo a los datasources rechaza perfiles fuera de `local`/`test`, URLs remotas, otro nombre de base, credenciales dentro de URL y destinos distintos para migración/runtime. Otro guard verifica por SQL que runtime no sea superusuario, BYPASSRLS, creador de bases/roles ni propietario de la base. Puerto HTTP inválido falla por binding; un JDK/Maven fuera de rango falla en Enforcer.

Esta protección de perfiles y el bind local son límites de desarrollo, no una implementación de autorización. Antes de añadir datos/APIs se requieren #26–30. Staging/prod y su configuración se incorporarán en sus issues de entrega y seguridad, revisando explícitamente esta restricción. No publicar este scaffold en una interfaz remota.

Actuator habilita acceso de lectura sólo a `health`, con probes `liveness`/`readiness`; no incluye detalles ni componentes. La raíz de health puede listar los nombres de esos dos grupos. Discovery HTTP y exposición JMX están deshabilitados; `env`, `configprops`, `beans`, `heapdump`, `metrics`, `shutdown`, etc. devuelven 404. Los errores HTTP no incluyen mensajes internos, excepciones ni stack traces. No se configura CORS ni se añade autorización ficticia.

## Migraciones y privilegios

JDBC + Flyway son suficientes: no hay JPA/Hibernate ni entidades artificiales. Flyway usa `fiscuvia_migrator`; el pool normal usa `fiscuvia_runtime`. El rol bootstrap de Compose se limita a aprovisionar roles en un volumen nuevo. V1 crea sólo el esquema vacío `app` y USAGE para runtime, sin permisos sobre futuras tablas. El historial vive en `infrastructure.flyway_schema_history`, inaccesible a runtime.

Migraciones en `src/main/resources/db/migration/V<N>__descripcion.sql`, LF y versiones únicas. `validate-on-migrate`, validación de nombres y ubicaciones activas; baseline y out-of-order deshabilitados; `clean-disabled=true`. No editar migraciones aplicadas ni reparar checksums para ocultar divergencias. El init SQL genérico de Spring está deshabilitado: Flyway es la única autoridad del esquema. [Guía completa, recuperación y limpieza](../infra/README.md).

## Estructura y límites

```text
backend/
├── .mvn/wrapper/maven-wrapper.properties
├── mvnw / mvnw.cmd / pom.xml / README.md
└── src/
    ├── main/
    │   ├── java/com/fiscuvia/
    │   │   ├── FiscuviaApplication.java
    │   │   └── platform/
    │   │       ├── package-info.java
    │   │       └── internal/ (guardas de entorno y privilegios de DB)
    │   └── resources/ (application*.yml y db/migration/V1__initialize_infrastructure.sql)
    └── test/
        ├── java/com/fiscuvia/ (HTTP, arquitectura, runtime y JAR ejecutable)
        ├── java/architecture/fixtures/ (violaciones deliberadas sólo de prueba)
        └── resources/application-test.yml
```

Se usa un artefacto Maven con módulos lógicos Spring Modulith. Cada subpaquete directo de `com.fiscuvia` es un módulo por detección estándar; la raíz sólo contiene el bootstrap. `platform` verifica entorno, contratos y privilegios, sin dependencias. `identity` depende únicamente del API público de platform; expone el puerto de operaciones autorizadas y mantiene seguridad/JDBC/HTTP internos.

Convención para las siguientes issues:

- Crear cada módulo cuando tenga comportamiento real, con `package-info.java` y `@ApplicationModule(allowedDependencies = {...})` explícito y mínimo.
- API pública en la raíz del módulo; implementación, configuración, repositorios y adapters bajo `internal`. Publicar interfaces adicionales sólo mediante `@NamedInterface` documentada.
- Referenciar únicamente APIs/eventos expuestos de otros módulos; evitar acceso a sus entidades/repositorios. No crear un paquete global de servicios de negocio.
- Actualizar el inventario de `ModuleBoundariesTests` y ejecutar `ApplicationModules.verify()` en cada cambio de límites. La detección estándar incluye módulos nuevos, no sólo una lista seleccionada manualmente.

| Límite futuro | Paquete previsto, todavía inexistente |
|---|---|
| Identidad / membresías; despacho | `identity`; `tenants` |
| Contribuyentes | `taxpayers` |
| Obligaciones; revisión/presentación | `obligations`; `workflow` |
| Tareas; documentos; honorarios | `tasks`; `documents`; `fees` |
| Reportes | `reports` |
| Notificaciones; jobs persistentes | `notifications`; `jobs` |
| Auditoría | `audit` |
| Extensiones posteriores CFDI / SAT | `cfdi`; `satconnector` |

`SYSTEM.md` usa `reports` y ADR-001 usa `reporting` como nombre conceptual. Se conserva `reports` como convención provisional de esta rama; no existe ese módulo y su nombre definitivo se revisará en la issue correspondiente. No se fijan dependencias entre futuros dominios ni APIs vacías. Los casos de ciclos/acceso interno viven fuera del paquete productivo y no entran al JAR.

## Pruebas y evidencia

```powershell
# Unitarias del guard de configuración
.\mvnw.cmd -B -ntp '-Dtest=ScaffoldRuntimeConfigurationTests' test
# Arquitectura positiva y casos negativos (ciclos / acceso interno)
.\mvnw.cmd -B -ntp '-Dtest=ModuleBoundariesTests' test
# Arranque Spring real, health, superficie Actuator y errores HTTP
.\mvnw.cmd -B -ntp '-Dtest=FiscuviaApplicationTests' test
# Build completo con PostgreSQL Testcontainers + arranque del JAR en proceso independiente
.\mvnw.cmd -B -ntp clean verify
# Dependencias efectivamente resueltas
.\mvnw.cmd -B -ntp dependency:tree
```

Surefire ejecuta `*Tests`; Failsafe ejecuta `*IT` después de empaquetar el JAR. Los tests usan HTTP y PostgreSQL reales en puertos efímeros de loopback, cierran contextos/procesos/contenedores y no reutilizan DBs. Los tests del JAR originales se conservan, ahora con una DB real y casos adicionales de conexión/credenciales/privilegios/URL remota. Se prueban checksum alterado y rollback SQL en contenedores nuevos por método. Docker ausente provoca fallo, no omisiones ni sustitución en memoria. La evidencia histórica de #20 está en [VALIDATION.md](VALIDATION.md); la vigente para #22 en [VALIDATION-22.md](VALIDATION-22.md).

## Alcance y siguientes issues

Trazabilidad: #20/#22/#26, ADR-003/007 como dirección (conservan Proposed), base NFR-001/021 y NFR-040/041. #26 demuestra aislamiento PostgreSQL de infraestructura/fixtures; no declara completo NFR-001 transversal ni FR/BR de negocio. No hay expedientes, comandos o contratos de dominio ni cambios UI. Gate #74 intacto.

- #22: PostgreSQL, Docker, Flyway y JDBC integrados; sin ORM ni tablas de dominio. Gate #74 antes del dominio fiscal.
- #23 integrado: [CI definido](../docs/delivery/CI.md), wrapper `clean verify` en Ubuntu 24.04 / Temurin 25.0.5+8 / Maven 3.9.16, Docker/Ryuk obligatorios. #24 amplía los mínimos a ocho suites/90 pruebas y conformidad HTTP, cero errores/omisiones; publica JUnit depurado. Remote CI pendiente #79.
- #24 integrado: [contratos/errores/validación](../docs/api/foundation-24.md), sin negocio ni autenticación.
- #25 integrado: [observabilidad, privacidad y runbook](../docs/delivery/OBSERVABILITY.md), [evidencia local](VALIDATION-25.md). JSON por allowlist; UUID de correlación conservado y trazas HTTP reales locales; métricas acotadas sin tenant. Health/readiness PostgreSQL real, sin detalles. Actuator administrativo cerrado; métricas únicamente con opt-in explícito, perfil local y puerto loopback separado. Sin exportadores, collectors ni cambios de esquema.
- #26 integrado: [tenancy/RLS](../docs/data-model/tenant-foundation-26.md), [evidencia](VALIDATION-26.md). No completa identidad, autorización por recurso ni seguridad transversal de #27–30.
- #27 integrado: OIDC/membresías/sesiones con IdP real local; producción pendiente. #28 aporta infraestructura de capacidad/recurso en esta rama; matriz funcional, auditoría/outbox #29 y regresión transversal #30 siguen pendientes.
- UI #21 está integrada; #52 y CFDI/SAT permanecen fuera de alcance de este cambio backend.

Metodología contrastada con [AGENTS](https://github.com/Dagt/TrailerSaas/blob/main/AGENTS.md), [flujo de issue/PR](https://github.com/Dagt/TrailerSaas/blob/main/docs/backlog/codex-workflow.md), POM y `ModuleBoundariesTests` de TrailerSaaS. Se adoptan la entrega acotada, la prueba arquitectónica y la evidencia por SHA. No se copian dominios, módulos logísticos ni infraestructura posterior.

## Sesiones e identidad #27

`DB_SESSION_PASSWORD` y `FISCUVIA_ACTOR_PROOF_KEY` son obligatorios incluso con login deshabilitado. Un volumen V2 requiere el [upgrade controlado y procedimiento de bootstrap](../docs/security/AUTHENTICATION.md#actualizar-un-volumen-v2-y-primer-administrador); no borrar datos ni editar V1/V2. Los generadores de entorno nuevos incluyen ambos valores privados. Health/info siguen públicos; `/me` requiere sesión y devuelve 401 sin ella. Todas las mutaciones requieren CSRF. [Modelo físico y límites](../docs/security/AUTHENTICATION.md).

## Autorización por recurso #28

[Modelo físico, contrato tipado, asignaciones y runbook](../docs/security/RESOURCE-AUTHORIZATION.md); [protocolo de validación](VALIDATION-28.md). V4 es aditiva y no cambia V1/V2/V3. No hay nuevas dependencias ni cambios de UI. El puerto público AuthorizedTenantOperations ahora recibe Capability y ResourceTarget; no expone el booleano privileged. Los permisos operativos no aprobados deniegan y sólo los fixtures desechables añaden combinaciones sintéticas. El SHA y los resultados finales se registran en el PR y summaries depurados, nunca en logs crudos publicados.

## Journal y outbox #29

Base #28 integrada en main `1f09a26fc0afbcc3498b825e1b3f676cda79d2f0`. Nuevo módulo audit (identity → audit → platform), V5 sin cambios a V1–V4. [Catálogo/modelo/privacidad](../docs/security/AUDIT-JOURNAL.md), [protocolo y runbook](../docs/delivery/OUTBOX.md), [comandos completos y mínimos 250](VALIDATION-29.md). No hay dependencias nuevas, lector AUDIT_VIEW ni cuenta/scheduler de worker. Los transportes sólo se prueban con roles efímeros sin permisos de negocio; scopes vacíos al instalar. El SHA probado y resultados finales se publican en el PR y summaries depurados.

## Regresión de amenaza #30

[Harness y cobertura](../docs/security/CROSS-TENANT-REGRESSION.md), [mutaciones](../docs/security/NEGATIVE-TESTING.md), [evidencia](VALIDATION-30.md). Ejecutar clean verify completo: mínimo 295 tests (250 previos + 45 nuevos), sin skip ni Docker opcional. Luego `node tools/ci/reports.mjs backend` desde raíz exige suites y métodos críticos y genera regression.json sanitizado por SHA. PostgreSQL/Keycloak reales y roles efímeros sólo en Testcontainers; no ejecutar mutaciones en base local persistente ni remota. No se cambian V1–V5, contratos productivos ni matriz fiscal.
