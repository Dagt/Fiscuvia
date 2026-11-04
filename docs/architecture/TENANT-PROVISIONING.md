# Provisioning y configuración de despachos — #31

Base: main `879faee5b2872c1783859b998bf09119257f2472` (PR #88). Alcance FR-001/010, BR-001/005; seguridad #26–30, presentación #52. ADR-005/007/015 conservan su estado documental. No hay alta pública, operador SaaS HTTP, acceso fiscal del operador, suscripción #32, administración completa #33 ni tablas fiscales (#74).

## Autoridad y decisiones

El propietario autorizó en el encargo #31 un procedimiento administrativo interno mínimo, separado del runtime. Se conserva la frontera de bootstrap #27: autenticación OIDC real primero; resolución de identidad global verificada; revisión administrativa independiente del issuer/subject exacto; aprobación registrada y credencial migradora custodiada. Conocer un UUID o presentar un correo no satisface esa revisión. La base comprueba que el usuario aprobado existe, tiene identidad externa y está ACTIVE. La credencial migradora, no el UUID de aprobación, constituye la autoridad administrativa de ejecución. Una aprobación falsificada por quien ya comprometió esa credencial está fuera de la garantía del runtime; la custodia/atribución humana del operador sigue siendo un gate de producción.

**Propuesta pendiente de aprobación expresa:** habilitar `TENANT_SETTINGS_MANAGE` exclusivamente a `FIRM_OWNER_ADMIN`, dentro de su despacho ACTIVE, con identidad y membresía ACTIVE y MFA verificable de los últimos diez minutos. Alcance: nombre comercial, correo de contacto opcional, teléfono opcional, zona horaria y locale `es-MX`. Supervisor, contador y auxiliar permanecerían DENY. No habilita estado, owner, roles, logo, expedientes ni reactivación. Motivo: configurar la identidad comercial sin crear una autoridad SaaS ni adoptar toda OQ-015. La migración **no concede** esa política mientras esté pendiente. `TENANT_READ` conserva la aprobación existente de #27 para los cuatro roles. Las pruebas positivas de edición usan una política explícitamente sintética sólo en PostgreSQL desechable; no acreditan aprobación funcional.

El estado ACTIVE/SUSPENDED corresponde exclusivamente a administración técnica interna solicitada en #31. No deriva de facturación ni de una cuota. Baja, borrado y portabilidad/retención OQ-016 no se implementan. Proveedor/MFA de producción OQ-014, matriz completa OQ-015 y gate fiscal #74 continúan pendientes.

## Modelo físico V6

V1–V5 no cambian. `app.tenants(tenant_id,created_at)` permanece como raíz y FK existente. El módulo real `tenants` depende de identity/audit/platform; sólo identity puede consumir el puerto RLS de bajo nivel. No hay ciclo Java. `/me` consume un contrato SQL estrecho de directorio propio; no depende del módulo Java tenants.

| Objeto | Función y frontera |
|---|---|
| `tenant_private.profiles` | Metadatos globales de la raíz, una fila por tenant, FK RESTRICT. Runtime y sesiones sin USAGE/SELECT/DML. FORCE RLS con política únicamente para migrador; no son expedientes accesibles mediante una excepción de RLS |
| `tenant_private.admin_commands` | Recibos administrativos globales por comando; huella SHA-256 de parámetros canónicos, aprobación y tenant generado. Sin datos arbitrarios ni autoridad runtime |
| `tenant_private.profile_commands` | Recibos por tenant/comando/actor/huella/versión aplicada; FORCE RLS con contexto tenant. Sin cuerpos HTTP o copias de contacto |
| `identity.own_firms()` | Sin parámetros de actor/tenant: identidad HMAC vinculada al xid, sólo sus membresías ACTIVE y roles con TENANT_READ; nombre/estado propios, incluidos suspendidos. No establece contexto operativo |
| `tenant_api.profile(uuid)` | Exige operación TENANT_READ ya autorizada y contexto coincidente; lectura específica, sin enumeración |
| `tenant_api.update_profile(...)` | Exige operación TENANT_SETTINGS_MANAGE, owner y MFA recientes; versión, recibo, journal y outbox en la misma transacción |
| `tenant_private.provision(...)` / `change_status(...)` | Procedimientos internos sólo con SESSION_USER migrador, sin actor HTTP ligado; SQL fijo, sin ejecución genérica, sin BYPASSRLS |

Nombre comercial 1–160 caracteres sin controles ni espacios exteriores, obligatorio en un alta nueva. No hay unicidad por nombre: dos despachos pueden llamarse igual sin compartir filas. Correo opcional ≤254; teléfono opcional 7–30 con caracteres telefónicos acotados; ocho zonas IANA mexicanas permitidas, default `America/Mexico_City`; locale `es-MX`. Versión entera exacta 0–9007199254740991. Instantes `timestamptz`, generados por PostgreSQL y transportados en UTC. No se añade RFC, razón social, dirección, slug, `branding_json` libre ni datos fiscales sin necesidad aprobada.

Una raíz V5 existente recibe perfil con nombre NULL: es información aún no registrada, no un nombre comercial inventado. La migración hace un backfill estructural transaccional: toma bloqueo DDL, retira temporalmente la cerca de la raíz para que el migrador pueda leer todas sus filas, inserta sólo metadatos vacíos y restablece exactamente la cerca V2 **antes del commit**. FORCE RLS permanece habilitado. Ninguna transacción runtime puede observar la retirada intermedia. Un error revierte DDL, backfill y funciones; el test de upgrade debe incluir una raíz V5 anterior. Las altas posteriores inicializan perfil mediante trigger privado.

Todas las funciones definer tienen `search_path=pg_catalog,pg_temp`, objetos calificados y EXECUTE revocado a PUBLIC. No reciben SQL textual. Guardas de arranque rechazan grants runtime/sesiones sobre metadatos, ausencia de FORCE, acceso a funciones administrativas y ausencia del trigger de inicialización. El migrador puede alterar DDL: esto no es una defensa contra DBA comprometido.

## Flujos y concurrencia

**Alta:** revisión administrativa → identidad ya verificada ACTIVE bloqueada → command/approval UUID explícitos → advisory lock de comando → huella/replay → UUID tenant generado en PostgreSQL → raíz/perfil → bootstrap owner #27 → `OWNER_BOOTSTRAPPED` → `TENANT_CREATED` con relación causal → outbox de ambos eventos → recibo → commit. Un replay idéntico devuelve el mismo tenant sin recrear membresía/eventos; parámetros diferentes con la misma clave son conflicto. Una transacción administra un tenant, nunca cambia de contexto a otro.

**Configuración:** sesión verificada → usuario ACTIVE → bloqueo de raíz/lifecycle → membresía ACTIVE → capacidad explícita → MFA → contexto RLS → validación de operación SQL → bloqueo/versionado/huella → cambio → `TENANT_PROFILE_UPDATED` → outbox → recibo → commit. Cada solicitud autoriza de nuevo, también los replays. El recibo devuelve la versión aplicada originalmente; GET devuelve el estado actual. No se devuelve un DTO antiguo ni se reutiliza un permiso serializado. La huella cubre versión y valores normalizados del comando.

Las operaciones de consulta toman FOR SHARE sobre la raíz; configuración toma FOR UPDATE desde el inicio de autorización, antes de membresía/política. Evita dos upgrades compartido→exclusivo que podrían producir deadlock. Suspensión toma FOR UPDATE: una operación autorizada antes de suspender finaliza primero; suspensión espera su commit/rollback. Si suspensión confirma primero, las operaciones siguientes se deniegan. No se promete cancelación retroactiva de una transacción ya autorizada. Se conservan locks de identidad, membresía, roles y política #28 y el contexto xid/HMAC #26/27.

**Suspensión/reactivación:** procedimiento administrativo privado, versión esperada, clave/huella y aprobación; cambia estado e incrementa versión; journal/outbox y recibo atómicos. No elimina usuarios, membresías, atribución, eventos ni documentos. No hay endpoint de estado ni botón de reactivación. Las invitaciones no se pueden aceptar en un despacho suspendido, incluso cuando la suspensión concurre con aceptación. La sesión global permite logout y consulta de membresías propias, pero no acceso operativo al despacho suspendido.

## API implementada y UI

- `GET /api/v1/me`: amplía membresías propias con `commercialName` y `tenantStatus`; sin UUID ajenos, correo OIDC, subject, issuer o token. Campos aditivos opcionales en OpenAPI para compatibilidad; servidor actual siempre los entrega.
- `POST /api/v1/auth/tenant`: devuelve la misma metadata autorizada; no permite elegir un despacho suspendido.
- `GET /api/v1/tenants/{tenantId}/settings`: sesión y TENANT_READ; campos acotados, `canManage` informativo y `logoReference:null`; Cache-Control no-store.
- `POST /api/v1/tenants/{tenantId}/settings`: JSON tipado, expectedVersion, Idempotency-Key UUID y CSRF. No acepta estado, owner, roles, colores o URL de logo. 400 formato/UUID/extra; 401 sesión ausente; 403 autoridad insuficiente/suspensión; 409 versión/clave; 422 constraints; 500 fijo seguro. CSRF puede rechazar una mutación anónima con 403 antes de autenticar, igual que #27.

No existe API para listar todos los despachos ni para crear uno como propietario anónimo. Un UUID del path es un candidato, nunca prueba de acceso. OpenAPI implementado y generación TS se verifican con las herramientas #23/24; el borrador fiscal no cambia.

React compone AppShell, PageHeader, Field, Button, StatusPill, ConfirmationDialog, Loading/Error/Permission y Notification de #52. No hay pantalla de operador ficticio. `/app` muestra nombres reales de membresías; selección requiere POST y revalidación. Cambio/401/403/revocación/suspensión retiran contexto y cancelan/eliminan consultas protegidas. Claves incluyen usuario y tenant. No hay persistencia local de selección ni tokens. Nombre largo se recupera por título y se ajusta en contenido; ausencia de nombre se identifica como pendiente. Las notificaciones de éxito requieren respuesta real del servidor; guardar/cancelar/confirmar no simulan persistencia.

**Marca:** sin almacenamiento privado aprobado no se habilita upload. `logoReference` sólo puede ser NULL; sin SVG, URLs externas, rutas locales, claves de objetos o colores arbitrarios. Fallback con icono Lucide y nombre real; Fiscuvia mantiene navy/teal/Inter. El upload privado debe abordar ownership, MIME/decodificación, tamaño, contenido activo, referencias tenant-safe y autorización de descarga en el módulo de archivos correspondiente.

## Auditoría y límites

Eventos efectivos nuevos: TENANT_CREATED, TENANT_PROFILE_UPDATED, TENANT_SUSPENDED, TENANT_REACTIVATED. El bootstrap sigue produciendo OWNER_BOOTSTRAPPED. Alta/estado usan actor_kind DATABASE_ADMIN, actor_id NULL y referencia de aprobación; la creación no finge que el primer owner ejecutó el procedimiento. Edición usa el actor interno verificado y correlación generada por servidor. No se incluyen nombres, correo, teléfono, RFC ni snapshots en eventos. El payload outbox continúa siendo exclusivamente auditEventId, transporte sin autoridad fiscal ni workers nuevos. No se emite TENANT_BRANDING_UPDATED separado porque no hay upload ni una segunda operación real de marca.

Si journal u outbox fallan, todo el comando hace rollback. Journal sigue sin SELECT/DML runtime, append-only lógico, no WORM. No se amplía AUDIT_VIEW. Suspender no destruye autoría; un recibo no concede autorización para ejecutar otra operación. El control de volumen, retención de recibos y custodia de aprobaciones requiere política operativa antes del piloto. No hay limpieza destructiva automática.

## Operación local

Requiere el entorno PostgreSQL/Keycloak local documentado en [autenticación](../security/AUTHENTICATION.md); proveedor productivo pendiente. Flyway aplica V6 con migrador separado; no borrar volumen ni reescribir V1–V5 para un upgrade. Mantener la clave HMAC persistente del entorno.

1. La persona propuesta inicia sesión OIDC real; sin membresía todavía no adquiere despacho.
2. Un administrador autorizado verifica fuera del navegador el issuer/subject en el IdP y su identidad global ACTIVE en la base privada. Registra una aprobación independiente. No aprobar un userId recibido de un solicitante sin esa verificación.
3. Con `psql` local y la credencial migradora, ejecutar [provision-firm.sql](../../infra/postgres/provision-firm.sql). `-W` solicita contraseña sin incluirla en argv, Git o logs. No usar runtime/sesiones ni credenciales productivas en desarrollo.

PowerShell (UUIDs de ejemplo sin autoridad; reemplazar por identidad ya verificada y aprobación real del entorno local):

```powershell
psql -h 127.0.0.1 -p 5432 -U fiscuvia_migrator -d fiscuvia_local -W `
  -v command_id=00000000-0000-4000-8000-000000000001 `
  -v verified_owner_id=00000000-0000-4000-8000-000000000002 `
  -v approval_reference=00000000-0000-4000-8000-000000000003 `
  -v 'commercial_name=Despacho local de prueba' -v contact_email= -v phone= `
  -v time_zone=America/Mexico_City -f infra/postgres/provision-firm.sql
```

POSIX:

```sh
psql -h 127.0.0.1 -p 5432 -U fiscuvia_migrator -d fiscuvia_local -W \
  -v command_id=00000000-0000-4000-8000-000000000001 \
  -v verified_owner_id=00000000-0000-4000-8000-000000000002 \
  -v approval_reference=00000000-0000-4000-8000-000000000003 \
  -v 'commercial_name=Despacho local de prueba' -v contact_email= -v phone= \
  -v time_zone=America/Mexico_City -f infra/postgres/provision-firm.sql
```

Usar el puerto loopback configurado si difiere de 5432. No ejecutar la plantilla con UUID inexistente: PostgreSQL la rechazará. Reintentar con la misma clave y parámetros; no generar otra clave para ocultar un resultado incierto. Para suspensión/reactivación, [change-firm-status.sql](../../infra/postgres/change-firm-status.sql) recibe tenant_id, command_id, approval_reference, expected_version y status (`SUSPENDED`/`ACTIVE`), bajo la misma custodia. Obtener versión con revisión administrativa privada, no con una API global nueva. Conservar el registro de aprobación fuera de logs públicos.

Fallo de migración: revisar historia y fuente en entorno sintético; no editar migración ya aplicada. Recuperación mediante cambio posterior revisado o restore probado; no hay downgrade destructivo automático. En conflicto 409 refrescar antes de editar; 403 comprobar membresía, estado, política aprobada y MFA sin copiar datos de otros despachos a logs. Un error seguro conserva correlation ID para diagnóstico, nunca SQL/driver/body.

## Pruebas y dependencia siguiente

`TenantProvisioningTests`: HTTP OIDC real, perfiles y SQL con roles separados, replay/concurrencia, otro tenant, usuario sin membresía, roles no owner, MFA insuficiente/vencido, CSRF/DTO, versiones, suspensión, journal/outbox fallidos, rollback y oráculos de grants/FORCE/directorio propio. `ProvisioningMigrationIT`: upgrade V5 con checksums, reejecución y DDL fallido. Se preserva la suite #26–30. Los fixtures positivos de política viven sólo en tests/harness, jamás en el JAR.

#32 deberá añadir sus propias decisiones comerciales y entitlements; no reutilizar suspensión administrativa como facturación automática. #33 deberá aprobar delegación/roles y operaciones de equipo; el bootstrap no se transforma en registro abierto ni un owner obtiene capacidades fiscales. Logo/retención/proveedor productivo requieren sus gates. La evidencia y resultados definitivos se registran en el PR y en la validación #31; una prueba histórica o una captura no acredita el SHA actual.
