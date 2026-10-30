# Operación y despliegue
## Entornos
local/CI/staging/prod separados por base, buckets, correo, claves y proveedores. TLS y secret manager; Docker reproducible, migraciones Flyway y health checks. Despliegue controlado con rollback probado; anotar SHA y versión de DB.
## Backups
Copias cifradas automáticas de BD y almacenamiento de objetos, restauración ensayada en entorno aislado con evidencia. Definir RPO/RTO y ventanas de soporte antes de vender SLA.
## Observabilidad
Base #25: logs JSON mediante allowlist, correlación del servidor, spans HTTP locales y métricas de HTTP/JVM/JDBC sin datos sensibles ni labels tenant. Health/readiness con PostgreSQL real; métricas sólo mediante opt-in local en puerto loopback separado. Ver [observabilidad y runbook](OBSERVABILITY.md). Sin exportación, dashboards, jobs ni alertas externas. Consumo por tenant, cuotas, envíos y alarmas son capacidades futuras sujetas a revisión de privacidad/cardinalidad.
## Incidentes
Severidad, responsable, comunicación a afectados, conservación de evidencias, postmortem; brechas revisadas con asesoría legal.
## Suscripciones
Nunca borrar datos por fallo de pago de inmediato. Definir estados grace/read-only/suspend, exportación, retención y eliminación contractual.
## Costos
Medición de RFC administrados, usuarios, GB/transferencia, jobs, correos y uso SAT posterior; cuotas y alertas de presupuesto.
