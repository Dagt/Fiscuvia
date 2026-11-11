# Identidad, sesiones y membresías — #27

## Extensión aprobada local/test #33

[Invitaciones por correo verificadas, V10 y límites](LOCAL-EMAIL-INVITATIONS-33.md). La aprobación expresa del 2026-11-11 permite recoger nombre opcional/correo verificado desde OIDC local mediante atestación HMAC separada por propósito y resolver un destinatario issuer/subject exacto; no unir identidades por email. Configuración opt-in `FISCUVIA_OIDC_CONTACT_POLICY=keycloak-local-verified-email-v1`, disabled por defecto. No se guardan contactos/tokens en Spring Session. Sólo hash del token, primera respuesta secreta, replay sin secreto y entrega manual; sin correo externo o producción. No concede MEMBERSHIP_VIEW, cambio de rol o reactivación ni resuelve OQ-014/015.

Base técnica autorizada por el propietario el 2026-10-30 en la revisión del PR #83. **Sólo local/test**. No se ha seleccionado proveedor para producción. ADR-005 conserva Proposed para los pendientes; OQ-014 sigue Open. No se habilitan staging/producción ni el esquema fiscal #74.

## Fronteras implementadas

`identity` depende únicamente del API público de `platform`. Los controladores, JDBC, pruebas de evidencia MFA y configuración de seguridad son internos. Desde #28 los módulos consumen `identity.AuthorizedTenantOperations.execute(tenant, capability, target, work)`: sesión/estado global, membresía, capacidad aprobada, recurso/MFA y después `platform.TenantTransactions`, en una transacción. [Frontera de recursos](RESOURCE-AUTHORIZATION.md). Un UUID recibido por HTTP nunca es autorización. V3 también exige el vínculo de actor/tenant autorizado dentro de app.establish_tenant_context para runtime; la implementación V2 original se conserva privada en infrastructure, sin EXECUTE runtime. El puerto inferior de #26 sigue siendo infraestructura confiable, no una alternativa para saltar esta autorización.

1. Spring Security 7.1.2 (BOM Boot 4.1.2) verifica Code, PKCE S256, state, nonce, firma RS256/JWKS, issuer, audience/azp y tiempos del ID token. El cliente es confidencial; React no recibe sus credenciales.
2. El login se inicia con POST+CSRF. Un indicador de sesión de un uso permite el siguiente redirect fijo. Callback, éxito y error tienen destinos locales explícitos, no un returnUrl aportado por el cliente.
3. El converter de `OAuth2LoginAuthenticationFilter`, antes de guardar SecurityContext, sustituye OidcUser por AppPrincipal mínimo. El repositorio de clientes autorizados descarta access/refresh tokens; no se usan APIs delegadas ni renovación OAuth.
4. Spring Session JDBC 4.1.1 usa conexión/rol/esquema privado, distinto de runtime y migrador. No se guardan ID/access/refresh tokens, OidcUser, email, nombre, contraseña ni cliente autorizado. Antes del callback se conservan temporalmente state, nonce y verificador PKCE: son material necesario del protocolo y se eliminan al consumirlo/fallar.
5. Cookie `__Host-FiscuviaSession`, Path=/, sin Domain, Secure, HttpOnly, SameSite=Lax. Se rota el ID al autenticar; CSRF se renueva. Inactividad: 15 min; límite absoluto: 8 h; privilegios administrativos requieren MFA de los últimos 10 min. No hay sesiones ni tokens en localStorage/sessionStorage.
6. Cada solicitud autenticada comprueba usuario ACTIVE, auth_version y expiración. Suspender/revocar membresía bloquea la siguiente operación de ese despacho; no convierte la identidad global en propietaria de otros. Para revocar todas las sesiones de una identidad, el administrador interno incrementa auth_version (o deshabilita el usuario); las sesiones se invalidan al siguiente uso. No hay panel de administración global.

## Esquema físico V3 y credenciales

V1/V2 no se modifican. `V3__identity_memberships_and_sessions.sql` crea:

| Namespace | Datos / acceso |
|---|---|
| identity_private | users, external_identities, proof_key, actor_context, owner_approvals, membership_events. Sin USAGE ni acceso directo de runtime/sesiones |
| identity_crypto | pgcrypto nativo SECURITY INVOKER; USAGE de schema privado, sin acceso runtime/PUBLIC ni a la clave |
| identity | memberships, membership_roles, invitations con tenant_id NOT NULL, PK/FK compuestas, índices, ENABLE/FORCE RLS |
| auth_sessions | Dos tablas e índices de Spring Session; CRUD sólo fiscuvia_sessions, sin acceso a identidad/app/infrastructure |

Global user UUID y vínculo único issuer/subject no conceden despacho. Una primera autenticación OIDC válida puede crear una identidad sin membresías. No existe autolink por correo, registro público de propietarios, promoción a owner ni tablas fiscales.

Runtime sólo puede SELECT de sus memberships/roles bajo RLS. Desde V4/#28, owner+MFA tampoco habilita lectura SQL general de otros miembros; MEMBERSHIP_VIEW está pendiente. Los comandos administrativos usan funciones estrechas, no un directorio indiscriminado. No tiene DML directo ni SELECT de invitaciones. Las escrituras usan funciones SQL específicas y parametrizadas de identity/identity_private, con search_path fijo, ejecución PUBLIC revocada y sin SQL dinámico. Las primitivas C de la extensión trusted pgcrypto pertenecen al bootstrap PostgreSQL y conservan su EXECUTE nativo PUBLIC: el migrador no es superusuario para cambiarlo. Su namespace tiene USAGE privado y las primitivas son SECURITY INVOKER, sin acceso a proof_key ni a datos tenant. Esto se comprueba en catálogo/guardas y mediante rechazo de una llamada runtime; no se afirma un REVOKE que PostgreSQL no permite. Migrador y runtime siguen NOSUPERUSER/NOBYPASSRLS/NOINHERIT, sin pertenencia a roles privilegiados.

### Resolver membresía sin circularidad RLS

Antes de tenant context, el servidor acredita el issuer/subject verificado mediante HMAC-SHA256 ligado al xid PostgreSQL y al instante MFA. La clave aleatoria de 32 bytes está en configuración privada y en identity_private.proof_key, inaccesible para runtime. No basta SET de sesión, un UUID o conocer el subject para fabricar la prueba. No hay endpoint de firma ni función SQL genérica de consulta privilegiada.

`identity.bind_actor` verifica la prueba, fija un actor inmutable por transacción y permite únicamente la lectura de sus membresías. `identity.authorize_tenant` bloquea/comprueba membresía ACTIVE y, si corresponde, rol/MFA antes de permitir TenantTransactions. Cambiar actor o despacho dentro de la misma unidad se rechaza. Los vínculos privados se invalidan al finalizar el xid y se limpian en lotes acotados; no se heredan en el pool.

Aceptar una invitación es la excepción de bootstrap previa a una membresía: una función específica bloquea el hash y verifica el destinatario autenticado exacto antes de establecer contexto y crear membresía/rol/consumo en la misma transacción. No acepta un tenant elegido por el solicitante. No es un bypass ni una función SECURITY DEFINER genérica.

La clave no es un token de usuario. Debe conservarse junto con el volumen de desarrollo; un valor distinto al inicializado impide arrancar, sin sustituirlo silenciosamente. La rotación en producción necesita procedimiento, custodia, coordinación y aprobación pendientes. Comprometer el proceso BFF o el migrador supera la frontera de RLS; no se promete resistir ejecución de código con sus secretos. El rol de sesiones también es un activo privilegiado de autenticación, aislado del SQL runtime.

## MFA local verificable y frontera de producción

Keycloak **26.8.1**, imagen fijada por digest, exclusivamente local. [Plantilla de realm](../../infra/keycloak/realm-template.json): Code confidencial + S256, registro/direct grants/implicit deshabilitados; contraseña y OTP nativos, LoA2 y AMR derivados de ejecuciones completadas (no un mapper de valores constantes). Se solicita prompt=login/max_age=0. Cliente mínimo LoA2; no se omite el factor por SSO.

Sólo `keycloak-local-otp-v1` con issuer local aprobado y evidencia criptográficamente validada acr=2, amr con pwd/otp y auth_time reciente habilita la evidencia local. Las funciones SQL vuelven a exigir su vigencia y rol propietario. No es una política genérica para aceptar claims de cualquier proveedor. Un perfil desconocido/unverified no concede privilegios. Las pruebas recorren el formulario OTP real, rechazan OTP incorrecto, MFA ausente/futuro/vencido y verifican los endpoints privilegiados.

Producción sigue bloqueada hasta aprobar proveedor, configuración/factores y significado de acr/amr/auth_time, recuperación, costos/MAU, invitaciones por email, revocación/back-channel, custodia/rotación y operación. No basta cambiar el texto de una variable para autorizar un despliegue; los perfiles/hosts remotos continúan rechazados.

## Login, logout e invitaciones

Contrato: [OpenAPI implementado](../api/openapi-v1.yaml). `/me` devuelve UUID interno, membresías ACTIVE autorizadas y señal MFA local; no issuer/subject, correo, tokens ni datos de otros usuarios. `/auth/tenant` valida una membresía concreta; no cambia un contexto de sesión PostgreSQL ni permite consultas genéricas.

`POST /auth/logout` exige CSRF, invalida sesión JDBC y cookie y devuelve 204. El logout aplicativo no afirma cerrar todas las sesiones SSO del IdP. En Keycloak local, el administrador puede cerrar la sesión del proveedor desde su consola local o el endpoint OIDC descubierto `end_session_endpoint` con client_id y post_logout_redirect_uri registrados; sin id_token_hint, Keycloak solicita confirmación. Un nuevo login del BFF exige contraseña y OTP aunque exista SSO. Logout/revocación federados automáticos de producción quedan sujetos al contrato del proveedor, sin guardar un ID token sólo para logout.

Invitar/revocar/suspender requiere owner ACTIVE + MFA reciente; roles invitables: supervisor, contador, auxiliar. Token aleatorio 256 bits, base64url de 43 caracteres; sólo SHA-256 en DB, caducidad 24 h y consumo atómico. Reutilización, destinatario distinto, caducidad y aceptación simultánea se rechazan sin enumerar usuarios. El token se entrega una vez en respuesta no-store: compartir por un canal interno autorizado, nunca en URL, logs o screenshots. No se envían correos. El receptor local se vincula a issuer/subject exactos; email de producción no está habilitado.

No se reactiva implícitamente una membresía suspendida/revocada al aceptar otra invitación. No se cambia un propietario mediante estos endpoints. Historial mínimo conserva autor de invitación, aprobación de primer owner y eventos de membresía; no sustituye la auditoría transversal futura. Administración completa y ciclo del último propietario pertenecen a #33; capacidades/expedientes a #28.

## Desarrollo y pruebas reproducibles

Con Docker Linux accesible, Java 25, Wrapper Maven 3.9.16, Node 24.13.1/npm 11.8.0:

```text
cd backend
mvnw.cmd -B -ntp clean verify                 # POSIX: ./mvnw -B -ntp clean verify
cd ..
npm ci --prefix tools/ci --ignore-scripts
npm ci --prefix web
npm exec --prefix web -- playwright install chromium
node tools/ci/auth-browser.mjs
# Desarrollo interactivo opcional: node tools/ci/auth-browser.mjs --serve
```

El último comando compila únicamente el harness del classpath de tests, crea PostgreSQL/Keycloak reales y un certificado efímero, arranca BFF HTTPS + Vite HTTPS mismo origen y ejecuta Playwright. Mantiene Ryuk/limpieza; no reutiliza contenedores. No inicia cuentas externas. Puerto frontend exclusivo 4174; puertos DB/IdP/backend efímeros ligados a loopback. El IdP usa localhost y el BFF 127.0.0.1 para separar dominios de cookies en desarrollo.

Contraseñas, OTP y llaves se generan en ejecución. El handoff `.ci-work/27-browser.json`, logs, keystores y classpath son privados/ignorados y no artifacts. El harness se elimina al terminar; sus aprobaciones administrativas son fixtures explícitos sólo para subjects de prueba, después de autenticación real. No están en el JAR. La prueba Java emula la excepción de cookies Secure de localhost de Chromium únicamente para el IdP HTTP local; BFF usa HTTPS y el cliente Java confía en su certificado específico, sin trust-all.

Las capturas del producto se escriben en [evidencia UX #27](../ux/evidence/issue-27/README.md). No se capturan pantallas con credenciales del IdP ni trazas de navegador con cookies. Para uso manual, los mismos servicios requieren realm/secret/certificado locales generados y las variables siguientes; el template no contiene cuentas ni contraseñas utilizables. No publicar start-dev.

| Variable privada/configuración | Uso |
|---|---|
| DB_SESSION_PASSWORD | Rol privado de sesiones, generado junto con las credenciales locales |
| FISCUVIA_ACTOR_PROOF_KEY | 64 caracteres hex aleatorios, misma clave persistida privada por entorno |
| FISCUVIA_OIDC_ENABLED | false por defecto; true sólo con emisor local real |
| FISCUVIA_OIDC_ISSUER | http://localhost:puerto/realms/fiscuvia-local, loopback exclusivamente |
| FISCUVIA_OIDC_CLIENT_ID / CLIENT_SECRET | Cliente confidencial local; nunca VITE_ ni Git |
| FISCUVIA_ORIGIN | Origen HTTPS local explícito, sin path/query/credenciales |
| FISCUVIA_OIDC_MFA_POLICY | unverified por defecto; perfil local revisado keycloak-local-otp-v1 |

### Actualizar un volumen V2 y primer administrador

1. Windows: `./infra/Upgrade-IdentityEnvironment.ps1`; POSIX: `sh infra/upgrade-identity-env.sh`. Conservan valores existentes. No regenerar la clave de un volumen que ya tiene V3.
2. `docker compose -f infra/compose.yaml --env-file infra/.env up -d --wait postgres` conserva el volumen. Ejecutar `docker compose -f infra/compose.yaml --env-file infra/.env exec -T postgres sh /opt/fiscuvia/upgrade-session-role.sh` para crear sólo el nuevo rol en el volumen anterior. En una DB nueva el init ya lo crea.
3. Arrancar BFF con configuración privada: Flyway aplica V3 y verifica la clave/roles. Sin OIDC configurado sólo funcionan health/info/estado/CSRF; login está denegado, no hay modo simulado.
4. La persona aprobada se autentica realmente y obtiene su UUID interno de `/api/v1/me`. El administrador comprueba el issuer/subject exacto en el IdP y DB privada y registra aprobación explícita independiente. Ningún correo ni UUID del navegador prueba autorización administrativa.
5. Administrador interno con acceso Docker/migrador: `docker compose -f infra/compose.yaml --env-file infra/.env exec postgres psql -X -U fiscuvia_migrator -d fiscuvia_local -v tenant_id=UUID_APROBADO -v user_id=UUID_VERIFICADO -v approval_reference=UUID_APROBACION -f /opt/fiscuvia/bootstrap-owner.sql`. Sustituir los tres UUID; el SQL parametriza/castea y hace rollback ante errores. No usa runtime, no expone endpoint ni acepta identidades no verificadas. Un segundo bootstrap del mismo despacho se rechaza.

No borrar el volumen para resolver errores de permisos o clave. Diagnóstico: comprobar perfil, disponibilidad/versión de PG, roles separados, V3/checksum, realm/redirect exactos, reloj y OTP, cookies HTTPS/host y token CSRF nuevo tras login. Para cambiar un secreto usar un procedimiento administrativo controlado; no imprimir variables privadas ni habilitar logs SQL/headers/cuerpos. Métricas mantienen plantillas fijas y nunca etiquetan usuarios, subjects, tenants, cookies o tokens.

## Evidencia y riesgos pendientes

[Validación local #27](../../backend/VALIDATION-27.md) registra comandos/resultados; el SHA exacto probado está en el PR y summaries sanitizados. Se preservan pruebas previas y se elevan mínimos CI para autenticación/migraciones/browser. Artifacts sólo contienen resumen y JUnit depurado; no se suben sesiones, logs crudos, fixtures, keystores ni reportes completos de navegador.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79.** Presupuesto $0 sin cambios. Linux nativo/Temurin de Actions no se presume aprobado por pruebas Windows con contenedores Linux.

Faltan decisión de proveedor/operación productiva, revisión humana de seguridad, límites de carga y rotación de claves, recuperación/MFA de producción y las capacidades de #28. No hay datos fiscales reales ni integración SAT; #74 permanece vigente. RLS complementa la autenticación/autorización y no resiste compromiso del BFF/migrador.

## Integración durable #29

[Journal](AUDIT-JOURNAL.md) registra cambios reales de invitación/membresía y bootstrap aprobado mediante V5, sin cambiar V1–V4 ni almacenar tokens/issuer/subject en eventos. Actor y correlación verificados se vinculan por xid antes del comando; triggers y outbox participan en su transacción. Denegaciones atribuibles de operaciones privilegiadas se registran después del rollback en una frontera global sin tenant solicitado, con revalidación y prueba específica. No se reemplaza OIDC ni se añade un lector AUDIT_VIEW.

El transporte outbox no recibe sesiones, SecurityContext ni AuthorizedAccess; no usa membresía owner para procesar y no puede abrir contexto de negocio. No hay cuenta/worker de producción configurado. Custodia/rotación de la clave de atestación existente y proveedor/MFA productivos siguen pendientes.


V9 — política limitada aprobada: [referencia del propietario](https://github.com/Dagt/Fiscuvia/pull/90#issuecomment-6798951955). TENANT_PLAN_VIEW sólo owner ACTIVE + MFA ≤10 minutos; trigger productivo de cupos en INSERT/UPDATE ACTIVE, sin cuotas comerciales por defecto. La aceptación conserva el flujo original y confirma reservas sin duplicar consumo; reactivaciones registran journal/outbox. [Detalles y límites](../architecture/TENANT-ENTITLEMENTS.md). No resuelve OQ-007/014/015, ADR-015 completa ni #74.
