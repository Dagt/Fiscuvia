# Fiscuvia backend — scaffold #20

Backend ejecutable local/test, sin lógica fiscal, persistencia ni endpoints de negocio. Esta rama prepara #20 para revisión; la issue sigue abierta hasta el proceso de revisión/merge del propietario.

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

Java 25 es compatible según los [requisitos oficiales de Boot 4.1](https://docs.spring.io/spring-boot/4.1/system-requirements.html). La compatibilidad efectiva se comprueba resolviendo dependencias, compilando con `release 25`, verificando módulos y ejecutando el JAR durante `verify`. No fue necesario activar el fallback Java 21 de ADR-002. Enforcer exige JDK 25 y Maven 3.9.16 para evitar variaciones accidentales.

El propietario autorizó JUnit 6/Jupiter con Boot 4.1.2 y Modulith 2.1.2 el 2026-10-29 en esta conversación de implementación. Sustituye el requisito inicial de JUnit 5: Boot 3.5.16 pasó la exploración inicial, pero fue la [última publicación OSS de la línea 3.5](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/). La [guía de Boot 4](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) documenta el cambio a JUnit 6. Los ADR-001/002/003 conservan su estado Proposed; esta selección no aprueba las decisiones de dominio ni resuelve globalmente OQ-022.

El parent de Boot y el BOM de Modulith fijan las dependencias transitivas y plugins del lifecycle; no se usan versiones dinámicas ni dependencias snapshot. El timestamp del archivo está fijado en el POM para reproducibilidad. Esto no garantiza bytes idénticos entre distintos JDK/proveedores o sistemas operativos.

## Instalación y ejecución

Instalar un JDK 25 completo y comprobar `java -version`. Si Java no está en PATH, configurar `JAVA_HOME` para la sesión apuntando a su instalación y añadir su directorio `bin` al PATH. No es necesario instalar Maven. La primera ejecución necesita HTTPS a Maven Central; posteriores builds utilizan la caché local de Maven.

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

Detener con Ctrl+C. `UP` indica salud del proceso de este scaffold; no certifica conectividad a DB/OIDC/SAT ni preparación para un piloto.

## Configuración y fallos de arranque

| Configuración | Valor / alcance |
|---|---|
| `spring.profiles.default` | `local`, sin variable obligatoria |
| `SPRING_PROFILES_ACTIVE` | opcional: `local`; `test` es activado por las pruebas |
| `SERVER_PORT` / `--server.port` | 8080 por defecto; 0 permite puerto efímero |
| `server.address` | 127.0.0.1 por defecto; desarrollo local en loopback |
| `application-local.yml` | inicialización inmediata para detectar errores al arrancar |
| `src/test/resources/application-test.yml` | puerto efímero y banner deshabilitado; no se empaqueta |

No hay secretos, `.env` requerido, URL de DB ni issuer OIDC. Los perfiles fuera de `local`/`test`, incluso combinados con `local`, fallan al crear el contexto; un puerto inválido falla por binding de Spring con código de salida no cero. Un puerto ocupado debe liberarse o cambiarse con `--server.port`. Un JDK/Maven fuera de rango falla en `validate` con diagnóstico de Enforcer.

Esta protección de perfiles y el bind local son límites de desarrollo, no una implementación de autorización. Antes de añadir datos/APIs se requieren #26–30. Staging/prod y su configuración se incorporarán en sus issues de entrega y seguridad, revisando explícitamente esta restricción. No publicar este scaffold en una interfaz remota.

Actuator habilita acceso de lectura sólo a `health`, con probes `liveness`/`readiness`; no incluye detalles ni componentes. La raíz de health puede listar los nombres de esos dos grupos. Discovery HTTP y exposición JMX están deshabilitados; `env`, `configprops`, `beans`, `heapdump`, `metrics`, `shutdown`, etc. devuelven 404. Los errores HTTP no incluyen mensajes internos, excepciones ni stack traces. No se configura CORS ni se añade seguridad ficticia.

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
    │   │       └── internal/ScaffoldRuntimeConfiguration.java
    │   └── resources/application.yml, application-local.yml
    └── test/
        ├── java/com/fiscuvia/ (HTTP, arquitectura, runtime y JAR ejecutable)
        ├── java/architecture/fixtures/ (violaciones deliberadas sólo de prueba)
        └── resources/application-test.yml
```

Se usa un artefacto Maven con módulos lógicos Spring Modulith. Cada subpaquete directo de `com.fiscuvia` es un módulo por detección estándar; la raíz sólo contiene el bootstrap. `platform` es el único módulo implementado: verifica los perfiles admitidos, no exporta API de negocio y declara cero dependencias de otros módulos.

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
# Build completo + pruebas anteriores + arranque del JAR en proceso independiente
.\mvnw.cmd -B -ntp clean verify
# Dependencias efectivamente resueltas
.\mvnw.cmd -B -ntp dependency:tree
```

Surefire ejecuta `*Tests`; Failsafe ejecuta `*IT` después de empaquetar el JAR. Los tests usan HTTP real en puertos efímeros y cierran los procesos creados. Los casos negativos de Modulith importan sus fixtures de test explícitamente: la verificación productiva conserva la exclusión estándar de clases de prueba. No hay mocks de negocio, base de datos en memoria ni pruebas deshabilitadas. Los resultados y verificaciones del wrapper se registran en [VALIDATION.md](VALIDATION.md).

## Alcance y siguientes issues

Trazabilidad: #20, NFR-040, configuración local de NFR-041, dirección ADR-001/002; límites ADR-003 y gate #74 respetados. Ningún FR/BR de negocio se declara implementado. Roles/tenant/expediente, auditoría de comandos, migraciones, OpenAPI de negocio y capturas UI no aplican a este cambio sin datos, comandos ni interfaz; siguen pendientes en sus issues.

- #22: PostgreSQL, Docker y Flyway; no se incluyen drivers, ORM, H2 ni migraciones aquí. Gate #74 antes del dominio fiscal.
- #23: CI; esta entrega no crea workflows de GitHub Actions.
- #24–25: contratos/errores de API y observabilidad ampliada.
- #26–30: RLS, OIDC, capacidades, auditoría/outbox y aislamiento entre despachos antes de operar sobre datos.
- UI #21/#52 y CFDI/SAT permanecen fuera de alcance.

Metodología contrastada con [AGENTS](https://github.com/Dagt/TrailerSaas/blob/main/AGENTS.md), [flujo de issue/PR](https://github.com/Dagt/TrailerSaas/blob/main/docs/backlog/codex-workflow.md), POM y `ModuleBoundariesTests` de TrailerSaaS. Se adoptan la entrega acotada, la prueba arquitectónica y la evidencia por SHA. No se copian dominios, módulos logísticos ni infraestructura posterior.
