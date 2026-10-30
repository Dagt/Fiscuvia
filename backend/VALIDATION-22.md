# Validación local — issue #22

Fecha: 2026-10-29, America/Mexico_City. Base: `main` en `6ac5dd5d3750b76e50494953c10724b25368bad3`, PR #76 integrado. Rama `issue-22-postgresql-docker-flyway`; el SHA final validado se registra en el PR Draft.

## Versiones efectivas

| Componente | Comprobado |
|---|---|
| Host de build | Windows 11 amd64, PowerShell 7 |
| Java / Maven Wrapper | Oracle Java 25.0.3+9-LTS-195 / Maven 3.9.16, wrapper 3.3.4 |
| Spring Boot / Modulith / JUnit | 4.1.2 / 2.1.2 / Jupiter 6.0.3 |
| Docker / Desktop / Compose | 29.2.0 / 4.60.1 / 5.0.2, engine Linux amd64 |
| PostgreSQL | 18.6, imagen oficial `18.6-alpine3.24` |
| Digest PostgreSQL | `sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5` |
| Flyway / JDBC PostgreSQL / Testcontainers | 12.4.0 / 42.7.14 / 2.0.5, resueltos desde el parent Boot |
| Smoke Java Linux | Temurin 25.0.3+9, JRE Alpine, readiness UP |

No se modifican Boot, Modulith ni Java respecto de la base autorizada. No se añade ORM/JPA. La compatibilidad se comprueba mediante resolución Maven y ejecución real, no sólo una matriz teórica.

## Build y pruebas

Desde `backend/`:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd -B -ntp dependency:tree '-Dincludes=org.flywaydb:*,org.testcontainers:*,org.postgresql:*,com.h2database:*,org.hibernate.orm:*' '-DoutputFile=target/dependency-versions.txt'
```

Equivalente POSIX: `./mvnw`. Los tests de PostgreSQL/JAR son obligatorios: Docker ausente causa fallo, sin `disabledWithoutDocker`, mocks de DB, tests omitidos ni fallback H2.

| Clase | Ejecuciones | Resultado |
|---|---:|---|
| `ScaffoldRuntimeConfigurationTests` | 15 | Perfiles originales más URLs locales/remotas/credenciales/opciones inválidas |
| `ModuleBoundariesTests` | 3 | Módulo productivo y fixtures negativos de arquitectura preservados |
| `FiscuviaApplicationTests` | 16 | HTTP, probes y superficie Actuator originales, ahora con PostgreSQL |
| `PostgresFoundationTests` | 3 | SQL/conexión/roles, denegación DDL/historial, historial V1, reejecución, aislamiento de config y ausencia de H2/HSQLDB/Derby |
| `FiscuviaApplicationIT` | 8 | JAR local saludable; fallos por perfil/puerto/DB inaccesible/credenciales de runtime y migración/rol administrativo/URL remota |
| `FlywayFailureIT` | 2 | Checksum alterado rechazado; SQL inválido revierte todo y preserva V1 |
| **Total** | **47** | **37 Surefire + 10 Failsafe; 0 fallos, 0 errores, 0 omitidas** |

Se conserva la cobertura de #20; únicamente se adapta el arranque a la dependencia PostgreSQL solicitada. Los tests usan el bootstrap de roles real de Compose y comprueban que el tag/digest coincide. Contenedores por clase/contexto, contenedor por método para corrupciones, DBs sin reuse y directorios temporales JUnit; los contextos y procesos del JAR se cierran. Ryuk mantiene la limpieza de emergencia. Los puertos PostgreSQL de test se vinculan expresamente a 127.0.0.1 y se comprueba el binding mediante Docker.

Los errores SQL deliberados del test negativo aparecen como ERROR de Flyway, seguidos de rollback exitoso y resultado de test aprobado. No son errores ignorados del build. El aviso existente Byte Buddy/Mockito de Java 25 sigue siendo de las herramientas de prueba; no se oculta mediante flags JVM.

## Compose y proceso real

Desde la raíz se ejecutaron:

```powershell
pwsh -File infra/New-LocalEnvironment.ps1
docker compose --env-file infra/.env -f infra/compose.yaml config --quiet
docker compose --env-file infra/.env -f infra/compose.yaml up -d --wait
docker compose --env-file infra/.env -f infra/compose.yaml ps
docker compose --env-file infra/.env -f infra/compose.yaml exec -T postgres pg_isready -h 127.0.0.1 -U fiscuvia_runtime -d fiscuvia_local
```

Resultados: servicio healthy, PostgreSQL 18.6 real y **único binding 127.0.0.1:55433->5432**. Se inspeccionaron flags de `pg_roles`: migrador y runtime no tienen superusuario, CREATEDB, CREATEROLE ni BYPASSRLS; el bootstrap administrativo es distinto. No se tocaron contenedores/puertos de otros proyectos.

El JAR se ejecutó directamente, desde `backend/`:

```powershell
java -jar target/fiscuvia-backend-0.0.1-SNAPSHOT.jar --server.port=18081
```

`GET http://127.0.0.1:18081/actuator/health/readiness` devolvió `{"status":"UP"}`. Se verificó migración con credencial separada y pool runtime normal, y después se detuvo únicamente ese proceso.

Historial SQL observado:

```text
installed_rank  version  type    checksum   success
0                       SCHEMA             true
1               1       SQL     421106078  true
```

Una versión SQL de aplicación, más la entrada automática de creación de namespace de Flyway. **Única tabla no sistémica: `infrastructure.flyway_schema_history`.** `app` está vacío. No hay tablas de dominio ni políticas RLS. El runtime no puede crear tablas ni leer el historial.

Se probó `restart`, seguido de `up -d --wait`; también `down` y `up -d --wait`. El volumen conservó V1, su checksum y una sola fila versionada. Compose 5.0.2 no acepta `restart --wait`; los comandos documentados separan reinicio y espera.

Finalmente se ejecutó el reset **sólo del proyecto creado para esta validación**, sin datos de negocio:

```powershell
docker compose --env-file infra/.env -f infra/compose.yaml down --volumes
docker volume ls --filter name=fiscuvia-local_postgres_data --format '{{.Name}}'
docker ps --filter name=fiscuvia --format '{{.Names}}'
```

Sin contenedor/volumen de esa validación al terminar. `infra/.env` local permanece ignorado para permitir un próximo arranque. No se ejecutaron podas globales ni se modificaron servicios ajenos.

## Comprobación Linux

PostgreSQL y su init shell se ejecutaron en el engine Linux real. Además se arrancó el mismo JAR con `eclipse-temurin:25.0.3_9-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0`, compartiendo temporalmente el namespace de red del contenedor PostgreSQL. `DB_PORT=5432` permite usar loopback sin habilitar URLs remotas ni publicar otro puerto. Mount del JAR readonly, `--env-file infra/.env`, sin imprimir credenciales.

```text
java -version: Temurin 25.0.3+9-LTS
GET http://127.0.0.1:18082/actuator/health/readiness -> {"status":"UP"}
```

El contenedor temporal fue detenido/eliminado. Esto verifica el runtime Linux, no una ejecución completa de Maven/Testcontainers con JVM Linux. Esa matriz de CI sigue pendiente de #23.

El generador POSIX se ejecutó también en Linux, en un directorio temporal separado: generó tres valores aleatorios y rechazó la segunda ejecución conservando el hash del archivo. Usa `/dev/urandom`, `od` y `tr`, sin requerir OpenSSL. La prueba equivalente del generador PowerShell también conservó el archivo existente.

## Seguridad y alcance

- Credenciales locales generadas aleatoriamente, sin valores reales/de producción en Git. Testcontainers genera otras UUIDs por contenedor.
- `git check-ignore infra/.env` confirma la exclusión. No se versionan volúmenes, dumps o backups.
- Comparación de valores generados contra logs Compose, logs del JAR y logs de los contenedores de tests: sin coincidencias. Las comprobaciones no imprimen secretos; los diagnósticos de tests redactan los valores antes de mostrarlos.
- Runtime sin privilegios administrativos/ownership; credenciales de migración distintas. Sin grants anticipados a tablas futuras. El bootstrap no se usa como datasource de aplicación.
- Guard de entorno ejecutado antes de conexiones; únicamente loopback y base `fiscuvia_local`, sin credenciales en URL ni parámetros arbitrarios. Staging/prod continúan bloqueados; tampoco hay despliegue.
- Sin frontend, contratos de negocio, esquema fiscal, RLS, OIDC ni ampliación de observabilidad. Gate #74, #26–30 y #23 conservan sus alcances; ADR Proposed no cambian de estado.

Revisión final: `git diff --check`, `git diff --cached --check`, archivos staged, ausencia de `.env` privado en Git y diff de `web/` vacío. En el PR se registran SHA y estado remoto observados.

**REMOTE CI NOT VERIFIED.** No hay workflows de Fiscuvia en esta entrega; #23 los implementará. No se atribuye la ausencia de ejecuciones a billing. Docker/SQL y pruebas locales sí se ejecutaron; la revisión final y el merge siguen siendo humanos.
