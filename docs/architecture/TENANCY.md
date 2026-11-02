# Aislamiento multi-tenant: especificación obligatoria
**Tenant = despacho. Taxpayer = contribuyente gestionado por ese despacho.**

Estado actual: #26–29 integrados; raíz mínima tenants, identidad/membresías, autorización por recurso y journal/outbox. [Infraestructura física](../data-model/tenant-foundation-26.md). #30 incorpora [regresión transversal y contratos futuros](../security/CROSS-TENANT-REGRESSION.md). Los puntos de archivos/cache/negocio siguientes son objetivos y no acreditan módulos productivos inexistentes.
1. Derivar despacho autorizado del usuario autenticado y membresías consultadas por servidor; no confiar en tenant_id proporcionado por UI.
2. Aislar tablas PostgreSQL con tenant_id y políticas RLS restrictivas; usuario de aplicación sin BYPASSRLS. Configurar tenant context de forma local a transacción y probar pooling/concurrencia.
3. Verificar rol, asignación a expediente y permiso para cada operación y exportación; pertenencia a despacho no equivale a acceso irrestricto.
4. Claves externas compuestas (tenant_id, id) evitan relaciones cruzadas. Índices por tenant + consultas.
5. Objetos S3/R2 privados con autorización previa y URLs firmadas de corta duración; la ruta o URL no es permiso.
6. Caches, búsqueda, jobs, outbox, notificaciones, exportaciones, métricas y temporales también aislados.
7. Operador SaaS sin acceso fiscal por defecto; break-glass con autorización, expiración y auditoría.
## Tests bloqueantes
Usuarios de A no pueden listar/ver/editar/exportar/descargar objetos de B, ni adivinar UUID, aunque ambos tengan el mismo RFC ficticio. Probar API, DB RLS con rol real, workers concurrentes, conexión reutilizada y links de archivos. Cualquier fuga bloquea piloto.

## Identidad integrada en #27 — sólo local/test

[Modelo efectivo](../security/AUTHENTICATION.md): el actor se verifica con OIDC y una atestación HMAC ligada al xid; la consulta bootstrap RLS sólo ve sus membresías. `identity.AuthorizedTenantOperations` autoriza ACTIVE/rol/MFA antes de TenantTransactions. V3 también protege `app.establish_tenant_context`: runtime necesita el tenant ya autorizado en el vínculo privado del actor; la función V2 original queda privada en infrastructure sin EXECUTE runtime. UUID/GUC solos no pueden abrir contexto, ni siquiera mediante SQL directo. Migrador conserva bootstrap administrativo explícito.

Las pruebas históricas #26 usan un adaptador interno de fixtures con pruebas HMAC y membresías sintéticas reales antes del puerto inferior. No es un filtro HTTP ni un bypass productivo; autenticación/cookies/firma/nonce/PKCE se prueban contra Keycloak real. Se conservan los 20 escenarios RLS y 2 de concurrencia/pool; #30 combina HTTP y efectos transaccionales de las capas posteriores.

## Autorización por recurso #28

El puerto público de identity ahora exige Capability y ResourceTarget; se elimina la variante pública de membresía+boolean privilegiado que podía confundirse con autorización completa. [Guía, SQL y límites](../security/RESOURCE-AUTHORIZATION.md). Sesión/usuario/membresía, política aprobada, rol, MFA y asignaciones se comprueban antes de TenantTransactions y se mantienen con bloqueos transaccionales. El contexto de operación no puede cambiarse/reutilizarse entre callbacks, actores, conexiones o tenants.

Cada tabla de recurso futura requiere además cerca RESTRICTIVE resource_allowed, con USING/WITH CHECK y políticas de comando explícitas. La demostración HTTP/SQL omite filtros de tenant/asignación y PostgreSQL sólo devuelve la cartera permitida; un oráculo detecta eliminar esa protección. No hay tablas fiscales productivas ni aprobación de la matriz de roles Proposed; #74 sigue pendiente. Journal/outbox #29 preservan esa frontera y transporte no concede autoridad de negocio. #30 prueba carteras, multidespacho, pool físico, threads y revocaciones con locks; [límites y mutaciones](../security/NEGATIVE-TESTING.md).
