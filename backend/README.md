# Fiscuvia backend — foundation #20 / #22

Backend ejecutable local/test con PostgreSQL real y Flyway. #20 está integrado; esta rama prepara #22 para revisión. Sin tablas fiscales, persistencia de negocio ni endpoints de dominio. Gate #74 y RLS #26 siguen pendientes.

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

Se usa un artefacto Maven con módulos lógicos Spring Modulith. Cada subpaquete directo de `com.fiscuvia` es un módulo por detección estándar; la raíz sólo contiene el bootstrap. `platform` es el único módulo implementado: verifica entorno y privilegios de conexión, no exporta API de negocio y declara cero dependencias de otros módulos.

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

Trazabilidad: #20/#22, ADR-003/007 como dirección (conservan Proposed), base NFR-001/021 y NFR-040/041. No se declara cumplido el aislamiento tenant NFR-001 ni ningún FR/BR de negocio: no hay datos por despacho, comandos, contratos de dominio o cambios UI. Gate #74 intacto.

- #22: PostgreSQL, Docker, Flyway y JDBC en revisión; sin ORM ni tablas de dominio. Gate #74 antes del dominio fiscal.
- #23: CI; esta entrega no crea workflows de GitHub Actions.
- #24–25: contratos/errores de API y observabilidad ampliada.
- #26–30: RLS, OIDC, capacidades, auditoría/outbox y aislamiento entre despachos antes de operar sobre datos.
- UI #21 está integrada; #52 y CFDI/SAT permanecen fuera de alcance de este cambio backend.

Metodología contrastada con [AGENTS](https://github.com/Dagt/TrailerSaas/blob/main/AGENTS.md), [flujo de issue/PR](https://github.com/Dagt/TrailerSaas/blob/main/docs/backlog/codex-workflow.md), POM y `ModuleBoundariesTests` de TrailerSaaS. Se adoptan la entrega acotada, la prueba arquitectónica y la evidencia por SHA. No se copian dominios, módulos logísticos ni infraestructura posterior.
