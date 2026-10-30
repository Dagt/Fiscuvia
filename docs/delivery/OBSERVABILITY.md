# Observabilidad de plataforma — #25

Estado: infraestructura local implementada para revisión. Trazabilidad: #25, épica #3, NFR-043/045 y dirección ADR-012 (conserva Proposed). No es auditoría fiscal ni monitoreo de módulos de negocio; staging/producción siguen bloqueados. No hay collector, dashboard, alertas externas ni datos por tenant.

## Arquitectura y versiones

Todo vive en `platform.internal.observability`, sin dependencias entre dominios. Spring Boot 4.1.2 administra Micrometer 1.17.2, Micrometer Tracing 1.7.2 y OpenTelemetry 1.62.0, compatibles con la base Java 25. Se añaden `spring-boot-micrometer-tracing` y `micrometer-tracing-bridge-otel`; `opentelemetry-sdk-testing` sólo para pruebas. Actuator/JDBC ya existían. No se añade un exporter, agente, servicio externo ni autoconfiguración OpenTelemetry basada en ambiente.

## Logs: proyección antes de serializar

`AllowlistLogFormatter` utiliza el soporte JSON nativo de Boot para consola y archivo. No serializa mensajes libres, argumentos, throwables, nombres arbitrarios de logger, atributos arbitrarios ni mapas MDC. También aplica a logs de dependencias: sus mensajes se convierten en eventos `RUNTIME_LOG` con componente acotado. Esto sacrifica detalle de mensajes y stacks del framework deliberadamente; no habilitar un appender alternativo para recuperarlos en un entorno con datos privados. Los avisos de la JVM y herramientas de build no son logs de aplicación y pueden usar texto.

Inventario permitido:

| Atributo | Origen permitido |
|---|---|
| `timestamp`, `application`, `severity` | UTC, constante `fiscuvia-backend`, nivel del evento |
| `component`, `module` | Clasificación cerrada APPLICATION/DATABASE/FRAMEWORK y platform/runtime |
| `event` | RUNTIME_LOG, HTTP_REQUEST_COMPLETED, HTTP_TIMEOUT, API_FAILURE, APPLICATION_READY, APPLICATION_FAILED |
| `correlation_id` | UUID generado por el servidor, contexto tipado |
| `trace_id`, `span_id` | Contexto de una traza real; ausentes sin ella |
| `method`, `route`, `status`, `outcome`, `duration_ms` | Métodos/rutas aprobados, estado HTTP y duración monotónica |
| `failure_code`, `error_fingerprint` | Categoría cerrada y hash de categoría/ubicación de código aprobada |
| `port` | Puerto efectivo al terminar el arranque |

No se registran RFC, CFDI, cuerpos, query strings, cookies, Authorization, credenciales JDBC, llaves o certificados. Los canarios de prueba son sintéticos. `SafeDiagnostics` clasifica configuración rechazada, autenticación/conectividad/privilegios DB, migraciones, timeout y error inesperado sin leer mensajes. La huella no permite reconstruir el mensaje; errores de ubicaciones no registradas pueden compartir huella. Registrar una nueva ubicación requiere revisión de código y privacidad. El público sigue recibiendo mensajes seguros RFC 9457, sin stacks.

Niveles: común/test root WARN y aplicación INFO; local root INFO. Los cambios de nivel no amplían atributos. No hay perfiles remotos habilitados ni endpoint para cambiar loggers. El formateador consulta contexto tipado en el hilo de emisión: cualquier futuro appender asíncrono debe proyectar esos campos antes de encolar, con pruebas propias.

## Correlación y trazas

`X-Correlation-Id` sigue siendo un UUID del servidor, igual al `traceId` público de Problem Details de #24. No se rompe ese contrato: el campo público no es el trace ID hexadecimal de OpenTelemetry. Los campos operacionales `trace_id`/`span_id` son independientes y permiten buscar un span real.

Se ignoran X-Correlation-Id, traceparent y baggage entrantes como fuentes de confianza. Cada solicitud inicia contexto local real; no hay propagación a destinos externos. El filtro instala contexto tipado/MDC después de la observación HTTP y lo restaura en finally. Redispatch/error/timeout asíncrono conserva el UUID y produce una sola terminación; listeners usan scopes cerrados. Pruebas concurrentes verifican aislamiento y limpieza.

`FISCUVIA_TRACING_SAMPLING_PROBABILITY` configura `management.tracing.sampling.probability`, por defecto 0.1, rango finito [0,1]. Sampling 0 conserva contexto real sin almacenar spans; 1 se usa en pruebas. Sólo se admiten observaciones HTTP; nombres y atributos de spans provienen de la misma lista cerrada de rutas/métodos. Sin atributos de URL, headers, cuerpos o query strings. La excepción entregada al span es una excepción sintética fija sin stack/cause; la excepción original conserva su tratamiento HTTP.

No hay procesador/exporter en ejecución normal, SDK global ni lectura de `OTEL_EXPORTER_*`. Se rechazan habilitaciones OTLP no implementadas. El test del JAR configura un destino señuelo y verifica que no reciba conexiones. `LocalTracingCustomizer` es un punto de extensión de código interno, usado por pruebas con un exporter en memoria. Un collector futuro necesita autorización de destino, revisión de atributos, retención, autenticación, egress y pruebas negativas antes de añadir un exporter.

Futuros jobs: capturar únicamente un contexto tipado autorizado, abrir/cerrar scope explícitamente en cada ejecución y conservar el vínculo causal con la traza cuando exista. Nunca copiar todo MDC, usar InheritableThreadLocal ni confiar en IDs de mensajes externos. Cada executor necesitará pruebas de reutilización de hilos, error, timeout y limpieza. No existen workers/colas en esta entrega.

## Métricas y cardinalidad

Registry simple en memoria; no registry global ni exportación de métricas. `PrivateMeterPolicy` admite nombres explícitos de HTTP, JVM/G1, memoria/buffers/hilos/clases, CPU/uptime, arranque, JDBC/Hikari y dos gauges de plataforma. Nombres desconocidos se rechazan y etiquetas ajenas se eliminan. Ver la clase para el inventario exacto.

HTTP: `http.server.requests` (cantidad/latencia, status/outcome/exception segura), `http.server.requests.active`. Las rutas aprobadas son public/info, health/liveness/readiness, metrics y su plantilla de consulta, y /error. Actuator usa plantillas catch-all: sólo coincidencias exactas del inventario pueden refinarlas. Cualquier ruta nueva o inexistente se agrupa en UNMAPPED; jamás se usa una URL arbitraria como etiqueta. Añadir un endpoint exige revisar el inventario. Errores HTTP permiten calcular tasas, sin un contador duplicado.

No se admiten tenant, UUID, RFC, documento, usuario ni correlation ID como labels. Tags de pools/nombres son constantes; regiones JVM/estados de hilos tienen conjuntos cerrados con OTHER. GC cause/action y otras dimensiones no aprobadas se descartan; pueden fusionarse series. Cambiar de GC o añadir métricas exige revisión explícita.

- `fiscuvia.database.available`: -1 antes de la primera comprobación, 1/0 según el último health check real. No es un sondeo periódico ni disponibilidad histórica.
- `fiscuvia.migrations.ready`: 0 antes del arranque completo, 1 después de Flyway/guardas y ApplicationReady. No revalida checksums continuamente.
- JDBC/Hikari: conexiones activas, idle, máximas, mínimas y métricas de pool aprobadas. No URLs, nombres de bases ni credenciales como tags.

## Health y diagnóstico local

Por defecto sólo `/actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness` están accesibles en la interfaz de aplicación loopback. Detalles/componentes nunca; la raíz puede enumerar únicamente grupos liveness/readiness. Readiness combina disponibilidad de aplicación y `SELECT 1` con timeout sobre el usuario runtime. PostgreSQL caído => 503 DOWN; liveness sigue UP si el proceso funciona. Fallo de DB al arrancar impide arrancar, sin fallback H2.

`env`, `beans`, `configprops`, `heapdump`, `loggers`, `metrics` y `prometheus` no están expuestos por defecto. Para diagnóstico manual local, copiar el ejemplo (no contiene secretos) a un archivo ignorado:

```powershell
# Desde la raíz; requiere PostgreSQL local configurado según infra/README.md
Copy-Item backend/config/local-observability.properties.example .ci-work/local-observability.properties
cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.config.additional-location=file:../.ci-work/local-observability.properties'
```

POSIX: `mkdir -p .ci-work`, `cp backend/config/local-observability.properties.example .ci-work/local-observability.properties`, `cd backend` y `./mvnw spring-boot:run '-Dspring-boot.run.arguments=--spring.config.additional-location=file:../.ci-work/local-observability.properties'`. En PowerShell crear `.ci-work` si no existe con `New-Item -ItemType Directory -Force .ci-work`.

Métricas: `http://127.0.0.1:18081/actuator/metrics` y `/actuator/metrics/http.server.requests`. En este modo health también se mueve a 18081; el puerto principal no sirve Actuator. El guard exige perfil exclusivamente local, opt-in explícito, ambos binds 127.0.0.1, puerto administrativo separado y acceso read-only. No hay autenticación administrativa todavía: sólo procesos/usuarios confiables en la máquina local; no usar túneles, proxies o publicación de ese puerto. Quitar el archivo adicional restaura el modo health-only. Los endpoints sensibles siguen cerrados.

Runbook: localizar APPLICATION_READY/FAILED; verificar código seguro de fallo y SHA/configuración sin volcar variables. Para incidentes HTTP, buscar correlation_id y luego trace_id si existe. Consultar readiness antes de investigar pool/DB; si DOWN, verificar Compose/health y credenciales mediante procedimientos locales sin imprimirlas. Para fallo Flyway revisar historial y migración fuente con DB desechable; no modificar una migración aplicada. Para latencia revisar conteo/tiempo por ruta y saturación de conexiones/JVM. No copiar logs crudos o volcados a artifacts públicos. No hay alertas/SLA ni despliegues habilitados.

## Verificación

`backend/mvnw clean verify` ejecuta pruebas JSON/privacidad, correlación con errores, 20 solicitudes concurrentes, timeout asíncrono, canarios en headers/query/cuerpo/excepciones, spans reales capturados en memoria, sampling, métricas acotadas, PostgreSQL detenido, Actuator y JAR real. Se conservan contratos HTTP, Modulith, Flyway/checksum/rollback y todas las suites anteriores. Ver [evidencia local](../../backend/VALIDATION-25.md), [estrategia](TEST-STRATEGY.md) y [CI](CI.md).

REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79. No se cambia presupuesto, workflows ni gates.
