# Autorización por recurso — infraestructura #28

## Consumidor efectivo #31

El módulo tenants consume `AuthorizedTenantOperations`, nunca `TenantTransactions`. TENANT_READ conserva sus cuatro roles aprobados; la propuesta de TENANT_SETTINGS_MANAGE owner+MFA permanece DENY hasta autorización expresa. No hay capacidad del operador ni roles comerciales nuevos. V6 ordena bloqueo de raíz antes de membresía/política y toma FOR UPDATE temprano para configuración; conserva el conjunto de recursos/locks de V4. La suspensión administrativa bloquea nuevas autorizaciones protegidas sin destruir asignaciones. [Contratos, límites y pruebas](../architecture/TENANT-PROVISIONING.md).

Base: main `06707ffcd221c55f6b4e1a6437a82c8571823bee` (#27 integrado). FR-004/006/072/074, BR-003/013/050/082 y ADR-005/007 describen trazabilidad; no se convierten sus propuestas completas en decisiones Accepted. #74 sigue bloqueando tablas fiscales; OQ-015 y la matriz funcional están pendientes.

## Autoridad y permisos efectivos

El catálogo `Capability` distingue códigos, **no otorga permisos**. V4 conserva exclusivamente autorizaciones técnicas de #27: TENANT_READ para las cuatro membresías iniciales; MEMBERSHIP_INVITE y MEMBERSHIP_DISABLE sólo owner, con MFA local reciente. La lectura de tenant significa validar el despacho/leer su raíz mínima, nunca acceder a todos sus expedientes. MEMBERSHIP_VIEW, MEMBERSHIP_ASSIGN_ROLE, TENANT_SETTINGS_MANAGE, TAXPAYER_*, DOCUMENT_*, REPORT_* y AUDIT_VIEW no tienen reglas productivas: DENY por defecto. No hay jerarquía, wildcard, ALL ni grants derivados del IdP.

La propuesta para aprobación futura es **capacidad explícita por rol y asignación exacta por membresía/recurso/operación**, sin acceso universal implícito para owner/supervisor. Alternativa: una capacidad tenant-wide separada, sujeta a aprobación expresa, MFA y pruebas propias. No se implementa esa alternativa. OQ-015 debe fijar tipos documentales, datos mínimos y acciones de auxiliares antes de otorgar acceso real. #28 implementa el mecanismo y demuestra sus invariantes con políticas marcadas SYNTHETIC TEST ONLY en PostgreSQL desechable, no decide esa matriz.

## Puerto y unidad transaccional

Los módulos consumen `identity.AuthorizedTenantOperations.execute(tenant, capability, target, work)`. Actor procede exclusivamente del principal mínimo de sesión OIDC; ningún parámetro actor/rol/permit es aceptado. `ResourceTarget` es un candidato, no una credencial. El backend fija capacidad/categoría según el comando. `AuthorizedAccess` entrega atribución tipada sólo dentro del callback; `requireCurrent()` rechaza su retención/reutilización fuera de esa transacción.

Orden: sesión vigente → usuario ACTIVE/auth_version → prueba HMAC ligada al xid → membresía ACTIVE → rol interno y política aprobada → MFA si requerido → asignaciones → TenantTransactions → consulta/comando. El puerto inferior sigue interno a infraestructura/identity; una regla ArchUnit impide dependencias de otros módulos a TenantTransactions. No hay API HTTP genérica para preguntar permisos, establecer actor o establecer contexto.

Todas las lecturas de autoridad y el trabajo ocurren en la misma conexión/transacción REQUIRED, read-write. No hay permiso cacheado en sesión, frontend, ThreadLocal, JWT ni DTO. Usuario, membresía, rol/política elegida y asignaciones relevantes quedan bloqueados FOR SHARE hasta commit/rollback. Una revocación confirmada antes del chequeo deniega; si la operación ya adquirió los bloqueos, la revocación espera y se ordena después. Esto evita TOCTOU, no promete cancelar retroactivamente una operación confirmada. No separar check y uso mediante transacciones independientes. Transacciones largas/exportaciones deben acotarse; jobs futuros reautorizan cada unidad, sin trasladar un permiso reutilizable.

El vínculo de operación es inmutable por xid: no cambiar actor, tenant, capacidad, categoría ni item/colección dentro de la unidad. Anidación idéntica puede participar; cambiar de alcance se rechaza y hace rollback. Los módulos validan estado real y expectedVersion después de autorización, dentro del callback; #28 no define estados fiscales.

## V4: modelo físico y privilegios

V1/V2/V3 no cambian. `V4__resource_authorization_foundation.sql` crea:

| Objeto | Frontera |
|---|---|
| access_control_private.role_capabilities | Política global revisada rol/capacidad/categoría, MFA y referencia de aprobación; sin acceso runtime/sesiones |
| access_control.resource_grants | tenant_id obligatorio, FK (tenant_id,membership_id), PK tenant-first/membresía/categoría/capacidad/recurso, índice tenant/categoría/recurso/membresía; active y referencia de aprobación UUID |
| access_control_private.operation_context | Vínculo privado actor/tenant/membresía/operación/recurso/xid y conjunto UUID autorizado congelado, inválido tras commit/rollback; limpieza acotada SKIP LOCKED, no auditoría |
| access_control.authorize_operation | Función estrecha, SQL fijo/parametrizado, sin argumento actor ni SQL arbitrario; devuelve código interno cerrado |
| access_control.resource_allowed | Predicado para cerca RLS de recurso; exige vínculo de operación, actor y tenant vigentes y asignación exacta |

No hay registros de contribuyentes/documentos, datos sembrados ni endpoints para modificar grants. El identificador genérico no sustituye futuras FKs concretas del módulo de dominio: antes de publicar uno, añadir referencias compuestas verificables y validar lifecycle/estado conforme a #74. Sólo migrador puede administrar políticas/asignaciones mediante cambio revisado; no se publica un CRUD de permisos ni se otorga esa facultad al owner automáticamente. Administración funcional corresponde a aprobación posterior/#33.

Grants tiene ENABLE/FORCE RLS, política owner acotada al tenant o membresía propia para resolver autorización antes del contexto, cerca restrictiva runtime false y ningún privilegio directo runtime. Sesiones no tienen USAGE de estos schemas. Funciones SECURITY DEFINER con propietario migrador NOSUPERUSER/NOBYPASSRLS, search_path pg_catalog,pg_temp, sin SQL dinámico y EXECUTE PUBLIC revocado. Guardas de arranque rechazan acceso directo a grants, schema privado, CREATE o drift FORCE/cerca. No se habilita BYPASSRLS ni se relajan las tablas de #26/#27.

V4 mueve los cuerpos de los comandos administrativos de #27 a identity_private, revoca su ejecución runtime y conserva las firmas públicas con wrappers estrechos. Además de los checks originales owner/MFA, éstos exigen el contexto de operación con la capacidad exacta: MEMBERSHIP_INVITE para crear/revocar invitación y MEMBERSHIP_DISABLE para suspender/revocar miembro. Ni SQL directo ni un contexto TENANT_READ pueden saltar esa comprobación. La aceptación de una invitación conserva su autoridad específica de bootstrap (hash/destinatario), no se transforma en permiso administrativo.

## Repositorios, búsquedas y archivos

Cada futura tabla de recurso necesita **ambas cercas RESTRICTIVE**: tenant_fence USING/WITH CHECK y resource_fence USING/WITH CHECK basada en resource_allowed con capacidad/categoría fijadas por servidor. Añadir políticas/grants explícitos por comando. Una lectura autorizada no debe satisfacer UPDATE/DELETE; permitir SELECT durante UPDATE sólo para la capacidad de edición autorizada, y mantener WITH CHECK. No copiar el CRUD de fixtures a producción.

Para listas/búsqueda/reportes/exportación usar target collection: la autorización comprueba capacidad, bloquea las asignaciones relevantes y RLS restringe filas antes de COUNT/ORDER/LIMIT/OFFSET o generación de archivos. Colección sin asignaciones devuelve vacío, sin revelar existencia. Para item, ausencia de asignación se oculta con 404. No convertir un permiso de lectura en exportación/descarga ni seleccionar primero toda la cartera y filtrarla en React. Lotes deben comprobar todos los targets para comandos atómicos y hacer rollback completo si alguno se deniega. Descargas futuras deben autorizar el recurso antes de generar una URL privada, con nueva comprobación al ejecutar un job.

RLS de recurso no aparece mágicamente en tablas nuevas: es contrato obligatorio y debe tener oráculos de omisión de filtros y mutación de políticas. #28 sólo tiene recursos sintéticos en `src/test/resources/resource-authorization-fixture.sql`, nunca en el JAR. No anuncia buscador, exportación, descarga documental ni CRUD fiscal operativo.

## HTTP y diagnóstico

401 sin sesión válida; 403 sin capacidad o contexto de colección; 404 para item no asignado, ajeno o inexistente; 409 para conflicto de estado/versión; 500 inesperado seguro. RFC 9457 conserva textos fijos ES-MX, no-store y correlación del servidor. No devolver razones internas, UUID ajenos, SQL, roles de terceros ni reglas detalladas. Los endpoints existentes de membresía conservan sus contratos #27. Los probes de recursos sólo viven en tests; no hay nuevas rutas productivas ni cambios de UI/OpenAPI de negocio.

ACCESS_DECISION registra únicamente PERMIT/DENY, categoría cerrada de operación/recurso y motivo cerrado, más timestamp UTC y correlación/tracing confiables del formatter existente. No registra IDs de usuario/recurso/tenant, estado privado, RFC, SQL, tokens, cuerpos ni parámetros. PERMIT significa chequeo aprobado, no commit de negocio. No es auditoría persistente; #29 deberá registrar eventos transaccionales y outbox sin usar estos logs como fuente de verdad. Métricas no añaden etiquetas por actor/tenant.

## Evidencia, operación y límites

Ver [pruebas y comandos](../../backend/VALIDATION-28.md). Se conserva OIDC real/firma/state/nonce/PKCE, sesiones, invitaciones y RLS anteriores. Las nuevas pruebas verifican HTTP y filas con runtime real, revocación concurrente antes del chequeo, ausencia de filtros, pool/concurrencia, estado/versión/rollback y un oráculo que detecta eliminar resource_fence. No hay filtros ficticios de autenticación.

Diagnóstico: comprobar primero correlación y reason_code cerrado; revisar estado/membresía/política/asignación sólo con procedimiento administrativo privado, nunca registrar filas o secretos. No arreglar un 403 mediante grants amplios, rol owner, apagar FORCE o reescribir migraciones aplicadas. Después de una corrección administrativa, usar nueva transacción y reautorizar. Upgrade V3→V4 conserva historial/checksum y rollback DDL; sin downgrade destructivo automático. No regenerar claves ni resetear volúmenes para ocultar fallos.

Riesgos: BFF/migrador comprometidos superan esta frontera; el runtime SQL no puede fabricar la prueba de identidad, ni escribir grants/políticas. Asignaciones genéricas requieren integración/FKs de cada módulo futuro; políticas mal diseñadas por un DBA no se vuelven seguras por estas guardas. Bloquear/leer asignaciones completas por colección tiene costo: medir cardinalidad, tiempos de bloqueo y bloat antes del piloto. Proveedor/MFA productivos, OQ-015/#74, auditoría #29 y regresión transversal #30 siguen pendientes.

Referencias primarias: [RLS y políticas restrictivas PostgreSQL 18](https://www.postgresql.org/docs/18/ddl-rowsecurity.html), [bloqueos de filas](https://www.postgresql.org/docs/18/explicit-locking.html), [arquitectura de autorización Spring Security](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html). Referencia TrailerSaaS: DatabaseTenantAuthorization y AuthorizationPostgresTests (scopes frescos y pruebas de revocación); se adapta al bootstrap de identidad de Fiscuvia y se añaden bloqueos/cerca por recurso, sin copiar jerarquía logística.

El conjunto de recursos de una colección se fija al autorizarla, además de bloquear los grants existentes: asignaciones nuevas concurrentes sólo aparecen en la siguiente unidad. El predicado RLS exige pertenencia a ese conjunto privado y grant activo. Se prueba una concesión confirmada durante un callback detenido sin ampliar sus filas. Medir memoria/almacenamiento del conjunto UUID antes de permitir carteras grandes; no trasladarlo a una cookie, log o token del cliente.

V4 restringe además la lectura SQL runtime de memberships a las propias, aun con owner/MFA/contexto administrativo de #27. MEMBERSHIP_VIEW de terceros sigue sin aprobarse. Los comandos estrechos conservan su lectura interna necesaria bajo migrador/FORCE; no conceden un directorio general. La prueba de owner con MFA y SELECT sin WHERE verifica que no aparecen otros miembros.

## Integración #29

[Journal durable](AUDIT-JOURNAL.md) recibe cambios reales de identidad en la misma transacción, sin convertir ACCESS_DECISION en historial. Rechazos privilegiados atribuibles registran una sincronización y persisten sólo después del rollback, mediante revalidación y prueba específica global, sin tenant/recurso solicitado. Un fallo de persistencia no concede acceso y no se presenta como evento durable.

AuditJournal.execute permite un comando interno idempotente después de esta autorización fresca; un replay no evita la membresía/capacidad actual. El recibo no es un AuthorizedAccess reutilizable ni una respuesta HTTP cacheada. La cuenta de transporte de outbox no usa este puerto ni adquiere autoridad de negocio; futuros trabajos deben definir autoridad/revalidación aprobada. AUDIT_VIEW sigue DENY, sin lector público. Las referencias a #29 anteriores son el estado histórico previo a esta integración.
