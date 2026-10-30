# Aislamiento multi-tenant: especificación obligatoria
**Tenant = despacho. Taxpayer = contribuyente gestionado por ese despacho.**

Estado #26: [infraestructura física implementada para revisión](../data-model/tenant-foundation-26.md): raíz mínima tenants, RLS forzada, cerca restrictiva, contexto JDBC transaccional e inmutable y pruebas PostgreSQL. Identidad/membresías #27, capacidades/expediente #28 y regresión transversal #30 siguen pendientes; los puntos siguientes describen el objetivo completo y no acreditan esas funciones.
1. Derivar despacho autorizado del usuario autenticado y membresías consultadas por servidor; no confiar en tenant_id proporcionado por UI.
2. Aislar tablas PostgreSQL con tenant_id y políticas RLS restrictivas; usuario de aplicación sin BYPASSRLS. Configurar tenant context de forma local a transacción y probar pooling/concurrencia.
3. Verificar rol, asignación a expediente y permiso para cada operación y exportación; pertenencia a despacho no equivale a acceso irrestricto.
4. Claves externas compuestas (tenant_id, id) evitan relaciones cruzadas. Índices por tenant + consultas.
5. Objetos S3/R2 privados con autorización previa y URLs firmadas de corta duración; la ruta o URL no es permiso.
6. Caches, búsqueda, jobs, outbox, notificaciones, exportaciones, métricas y temporales también aislados.
7. Operador SaaS sin acceso fiscal por defecto; break-glass con autorización, expiración y auditoría.
## Tests bloqueantes
Usuarios de A no pueden listar/ver/editar/exportar/descargar objetos de B, ni adivinar UUID, aunque ambos tengan el mismo RFC ficticio. Probar API, DB RLS con rol real, workers concurrentes, conexión reutilizada y links de archivos. Cualquier fuga bloquea piloto.
