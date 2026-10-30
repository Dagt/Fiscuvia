# PostgreSQL local — issue #22

Entorno exclusivamente de desarrollo, con PostgreSQL oficial **18.6-alpine3.24** fijado por tag y digest multi-arquitectura:

```text
sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5
```

Requiere Docker Engine/Desktop con contenedores Linux y Compose con `up --wait`. Validado con Docker 29.2.0, Desktop 4.60.1 y Compose 5.0.2. El puerto por defecto es **127.0.0.1:55433**, para no interferir con otros proyectos en 5432/55432. Nunca cambiar el bind a todas las interfaces para resolver un conflicto; cambiar sólo `DB_PORT`.

## Preparar credenciales sintéticas

Desde la raíz del repositorio, Windows con PowerShell 7:

```powershell
pwsh -File infra/New-LocalEnvironment.ps1
```

Linux/macOS con shell POSIX, `/dev/urandom`, `od` y `tr`:

```sh
sh infra/new-local-env.sh
```

Ambos generan `infra/.env`, ignorado por Git, con tres contraseñas aleatorias distintas de 32 bytes y `DB_PORT=55433`. No imprimen los valores y se niegan a sobrescribir un archivo existente. `.env.example` documenta nombres, sin contraseñas válidas. No es necesario copiarlo si se usa el generador. En POSIX el archivo se crea con permisos restringidos por `umask 077`; en Windows se conserva la ACL del directorio privado del usuario. No usar credenciales/datos de producción.

| Variable | Consumidor | Finalidad |
|---|---|---|
| `DB_PORT` | Compose / backend local | Puerto host de loopback, 55433 por defecto |
| `DB_BOOTSTRAP_PASSWORD` | Sólo contenedor PostgreSQL | Inicialización administrativa del volumen nuevo |
| `DB_MIGRATION_PASSWORD` | PostgreSQL / Flyway | Rol `fiscuvia_migrator`, sin superusuario |
| `DB_RUNTIME_PASSWORD` | PostgreSQL / datasource | Rol restringido `fiscuvia_runtime` |

No publicar `docker compose config` sin `--quiet`: su salida expandida puede contener contraseñas. Tampoco adjuntar `.env`, `docker inspect` completo, dumps, backups o logs de depuración que incluyan variables a un PR.

## Arrancar, comprobar y conectar

Estos comandos son iguales en PowerShell y POSIX, desde la raíz:

```sh
docker compose --env-file infra/.env -f infra/compose.yaml config --quiet
docker compose --env-file infra/.env -f infra/compose.yaml up -d --wait
docker compose --env-file infra/.env -f infra/compose.yaml ps
docker compose --env-file infra/.env -f infra/compose.yaml exec -T postgres pg_isready -h 127.0.0.1 -U fiscuvia_runtime -d fiscuvia_local
```

El health check usa TCP al servidor final; durante el bootstrap temporal sólo hay socket local, por lo que no se declara saludable antes de terminar la inicialización. `pg_isready` comprueba disponibilidad, no sustituye la autenticación/consulta real que verifican Spring y los tests.

Después, desde **`backend/`**:

```powershell
.\mvnw.cmd spring-boot:run
```

POSIX: `./mvnw spring-boot:run`. El perfil `local` importa `../infra/.env` como propiedades; las variables del proceso tienen precedencia. Para ejecutar el JAR, compilar con `clean verify` y lanzar `java -jar target/fiscuvia-backend-0.0.1-SNAPSHOT.jar` desde el mismo directorio. Si 8080 está ocupado, añadir `--server.port=18081` al comando del JAR. Salud y readiness: `/actuator/health` y `/actuator/health/readiness`; sólo se expone health, sin detalles internos.

La URL local está restringida a loopback y a la base `fiscuvia_local`; no se admiten URLs con credenciales, opciones arbitrarias ni conexiones de migración a otra base. El guard de perfiles se ejecuta antes de abrir conexiones: staging/prod continúan deshabilitados. Falta de configuración, DB caída, contraseña inválida o rol runtime administrativo hacen fallar el arranque, sin fallback a H2.

Para una consola administrativa **local**, sin pasar contraseña por argumentos:

```sh
docker compose --env-file infra/.env -f infra/compose.yaml exec postgres psql -U fiscuvia_bootstrap -d fiscuvia_local
```

El bootstrap usa autenticación local dentro del contenedor para aprovisionar roles. Las conexiones de aplicación usan TCP y contraseña SCRAM. No usar el rol bootstrap en Spring.

## Roles, volumen y migración

- `fiscuvia_bootstrap`: superusuario necesario sólo para la inicialización de la imagen. Su credencial no se configura en ningún datasource Spring.
- `fiscuvia_migrator`: propietario de la base/esquemas, `NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS`. Flyway usa su datasource separado.
- `fiscuvia_runtime`: mismas restricciones de flags, sin ownership ni privilegios de DDL. Sólo CONNECT a esta DB y USAGE sobre el esquema vacío `app`; sin acceso al historial Flyway. Los futuros grants se revisarán con cada migración, sin permisos automáticos sobre futuras tablas.

Los roles y la revocación de acceso público se aprovisionan en `postgres/init/10-create-roles.sh` **sólo al crear un volumen vacío**. Ese script no crea tablas de negocio. Flyway gobierna el esquema a partir de ahí.

El volumen nombrado `fiscuvia-local_postgres_data` se monta en `/var/lib/postgresql`, ruta de la imagen PostgreSQL 18 (no la antigua ruta `.../data`). La versión/digest del fixture Testcontainers debe coincidir con Compose; un test detecta divergencias. No cambiar de versión mayor reutilizando el volumen: revisar previamente la ruta oficial de actualización, respaldo/restauración y las issues operativas.

La única migración productiva es `backend/src/main/resources/db/migration/V1__initialize_infrastructure.sql`. Crea el namespace vacío `app`, propiedad del migrador, concede sólo USAGE al runtime y protege el namespace `infrastructure`. Tiene una finalidad verificable sin fijar el modelo fiscal. Flyway crea `infrastructure.flyway_schema_history` y una entrada de creación del esquema, además de V1; esto no significa que haya dos versiones de aplicación.

Consulta del historial:

```sql
SELECT installed_rank, version, type, checksum, success
FROM infrastructure.flyway_schema_history ORDER BY installed_rank;
```

Convención: `V<N>__descripcion_en_snake_case.sql`, números únicos consecutivos acordados al integrar cada PR. Mantener LF, SQL transaccional y una finalidad revisable por versión. Nunca editar una migración aplicada ni usar `repair`, baseline o `clean` para esconder un checksum roto. Añadir una nueva versión cuando exista una enmienda aprobada. Los tests prueban checksum inválido, rollback SQL y reejecución sin duplicar V1.

**Gate #74 intacto:** no hay tablas definitivas de usuarios, membresías, contribuyentes, obligaciones, declaraciones, pagos, CFDI u honorarios. No hay políticas RLS (#26). La única tabla no sistémica es el historial de Flyway. JDBC es suficiente; no se añade JPA/Hibernate sin entidades que lo justifiquen.

## Parar, reiniciar y limpiar

Desde la raíz:

```sh
# Parar sin quitar el volumen
docker compose --env-file infra/.env -f infra/compose.yaml stop
# Reiniciar y después esperar salud (restart no admite --wait en Compose 5.0.2)
docker compose --env-file infra/.env -f infra/compose.yaml restart
docker compose --env-file infra/.env -f infra/compose.yaml up -d --wait
# Quitar contenedor/red y conservar los datos
docker compose --env-file infra/.env -f infra/compose.yaml down
# Reconstruir contenedor usando el volumen conservado
docker compose --env-file infra/.env -f infra/compose.yaml up -d --wait
```

**Borrado intencional de todos los datos de este proyecto local**, con el backend detenido:

```sh
docker compose --env-file infra/.env -f infra/compose.yaml down --volumes
```

Este comando sólo debe usarse sobre `fiscuvia-local`, después de comprobar `compose ps` y que no existen datos a conservar. No ejecutar `docker system prune`, borrados globales ni comandos de otros proyectos. Para empezar de nuevo, reutilizar el `.env` local y ejecutar `up -d --wait`; los roles se recrean en el volumen vacío.

Si se perdió/cambió `.env` pero el volumen existe, generar otras contraseñas **no las cambia dentro de PostgreSQL**. Recuperar el archivo original o, tratándose únicamente de datos sintéticos prescindibles, hacer el reset explícito anterior. No automatizar pérdida de datos para resolver un fallo de login. Si hubo un fallo de init, revisar logs sin publicarlos y reconstruir sólo el volumen local incompleto cuando sea seguro.

## Pruebas y preparación de CI #23

Desde `backend/`, con Java 25 y Docker disponibles:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

POSIX: `./mvnw -B -ntp clean verify`. No necesita Compose ni `.env`: Testcontainers crea DBs aisladas con puertos efímeros **en 127.0.0.1**, contraseñas UUID y el mismo script de roles. El perfil `test`, sólo en recursos de prueba, usa `TEST_DB_*` suministrados dinámicamente. No toma el puerto/credenciales de desarrollo. Los contenedores se cierran por JUnit/Ryuk, sin reuse; los tests de corrupción SQL usan un contenedor nuevo por método. No hay mocks de PostgreSQL ni pruebas omitidas si falta Docker: el build falla.

Se conservan los tests HTTP, arquitectura y JAR de #20, adaptados a la dependencia real de DB. Hay comprobación SQL de roles y denegación de DDL/lectura del historial, conexión real, historial/checksum, reejecución, rollback, limpieza Flyway deshabilitada, ausencia de H2/HSQLDB/Derby, URLs/perfiles seguros y fallos del JAR. Las pruebas verifican que contraseñas aleatorias no aparezcan en logs de arranque/PostgreSQL.

Para #23: runner efímero Linux o Windows con JDK 25 y daemon Docker accesible (contenedores Linux), permisos para crear/remover contenedores/redes y ejecutar Ryuk, salida HTTPS a Maven Central y los registros de PostgreSQL/Ryuk. Docker Desktop debe estar iniciado en Windows. No necesita secrets de GitHub, PostgreSQL como servicio adicional ni variables de producción; los tests se autoaprovisionan. Mantener permisos de GitHub mínimos, por ejemplo `contents: read` para checkout; el acceso al socket Docker es una capacidad administrativa del host y debe limitarse al runner aislado. No desactivar Ryuk, tests, validación Flyway o guardas para hacer pasar CI. Reportes: `target/surefire-reports/` y `target/failsafe-reports/`, con tratamiento de logs sin secretos.

No se crea workflow en #22. **REMOTE CI NOT VERIFIED** hasta que existan ejecuciones verificadas del SHA en #23. Evidencia local y límites Linux: [backend/VALIDATION-22.md](../backend/VALIDATION-22.md).

Fuentes técnicas: [imagen oficial PostgreSQL](https://hub.docker.com/_/postgres), [inicialización Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [módulo PostgreSQL de Testcontainers](https://java.testcontainers.org/modules/databases/postgres/). Se reutilizan de TrailerSaaS las credenciales runtime/migración separadas, Flyway como única autoridad y Testcontainers obligatorio; no se copia su modelo logístico ni sus migraciones de dominio.
