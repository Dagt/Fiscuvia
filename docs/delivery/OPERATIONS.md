# Operación y despliegue
## Entornos
local/CI/staging/prod separados por base, buckets, correo, claves y proveedores. TLS y secret manager; Docker reproducible, migraciones Flyway y health checks. Despliegue controlado con rollback probado; anotar SHA y versión de DB.
## Backups
Copias cifradas automáticas de BD y almacenamiento de objetos, restauración ensayada en entorno aislado con evidencia. Definir RPO/RTO y ventanas de soporte antes de vender SLA.
## Observabilidad
Logs JSON redactados, correlation id, métricas/trazas, errores, tiempos API, fallos de trabajo asíncrono, consumo por tenant, cuota de almacenamiento, tasa de envíos y alarmas de costo.
## Incidentes
Severidad, responsable, comunicación a afectados, conservación de evidencias, postmortem; brechas revisadas con asesoría legal.
## Suscripciones
Nunca borrar datos por fallo de pago de inmediato. Definir estados grace/read-only/suspend, exportación, retención y eliminación contractual.
## Costos
Medición de RFC administrados, usuarios, GB/transferencia, jobs, correos y uso SAT posterior; cuotas y alertas de presupuesto.
