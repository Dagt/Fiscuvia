# Invitaciones por correo verificadas — #33, corte autorizado

## Administración de equipo #33 — ampliación aprobada

Ampliación autorizada posterior: V11 implementa A, sin alterar las restricciones de correo V10. Los límites de administración general descritos en el corte inicial siguiente son históricos; ahora el propietario aprobó ese alcance limitado. No hay correo productivo. [Controles y alcance](TEAM-ADMINISTRATION-33.md).

Base: `19d6ec043ccbfda3c75c39b3e5428a72666cd3ba` (#32), rama `issue-33-team-management`. FR-005/011, BR-003/004, NFR-007. Aprobación expresa del propietario en conversación del **2026-11-11**, exclusivamente desarrollo/pruebas con Keycloak real local. [Matriz y decisiones](TEAM-POLICY-PROPOSAL-33.md). OQ-014 no se resuelve ni se elige un proveedor de producción.

## Alcance real y límites

Se implementa el formulario de invitación por correo y la revocación de la invitación que se acaba de crear. Reutiliza MEMBERSHIP_INVITE owner+MFA, identidad/membresía/tenant ACTIVE, la invitación original y la aceptación OIDC existente. No instala MEMBERSHIP_VIEW, MEMBERSHIP_ASSIGN_ROLE ni autoridad de reactivación. El directorio de equipo/invitaciones y los cambios de rol/reactivación permanecen pendientes de autorización; no hay listados vacíos o botones que simulen esas funciones. Cartera sigue bloqueada por #74/OQ-013/015, sin tablas fiscales.

**Correo como localizador, nunca autenticación:** el destinatario debe haber completado OIDC real y tener correo verificado por el emisor local controlado. Se resuelve en backend y se fija issuer/subject exacto. Cambiar o conocer un correo no cambia la identidad global ni el receptor de un token ya emitido. Ambigüedad, falta de verificación, usuario desconocido o ya incorporado devuelven el mismo error seguro de validación sin directorio global.

## Configuración cerrada

Por defecto `FISCUVIA_OIDC_CONTACT_POLICY=disabled`: no se guardan contactos ni se permite el flujo por correo. Para el entorno local OIDC ya configurado, establecer explícitamente `keycloak-local-verified-email-v1`. Requiere OIDC habilitado y el issuer loopback exacto permitido por IdentitySettings. Valores desconocidos fallan al arrancar. Los perfiles remotos siguen prohibidos; esta opción no habilita producción.

El cliente local solicita openid/email/profile sólo con esa política. El callback ya verifica Code+PKCE/state/nonce/firma/issuer/audience/tiempos; sólo después se ingieren email_verified=true y correo válido. Dominio ASCII en minúsculas; local-part exacto, sin colapsar alias/puntos. Nombre opcional ≤160 y correo ≤254; no se conserva nombre si el correo no es verificable. Un contacto que pierde verificación se retira en el siguiente login comprobado. No se guardan esos claims, tokens ni OidcUser en Spring Session.

No existe consulta al API administrativo de Keycloak ni cuenta técnica para buscar personas. Sólo se resuelven contactos ya verificados en esta aplicación; el destinatario nuevo primero inicia sesión. Un vínculo de varios emisores o múltiples identidades con el mismo correo falla cerrado hasta definir una política distinta.

## Modelo físico V10

V1–V9 intactas. `V10__verified_local_email_invitations.sql` añade:

- `identity_private.verified_contacts`: PK/FK issuer/subject exactos, email, display_name opcional y verified_at servidor. Metadata global de identidad, no datos tenant-owned. ENABLE/FORCE RLS y política sólo migrador; runtime/sesiones sin lectura/DML directo ni acceso de schema privado.
- `identity_private.email_invitation_commands`: recibo tenant/command/actor/huella/ID de invitación; FK compuestas a invitación y journal, FORCE RLS tenant-scoped. Sin token, copia de correo/nombre ni respuesta HTTP serializada.
- `identity.record_verified_local_contact`: función estrecha con atestación HMAC separada por propósito, ligada a issuer/subject/xid/email/nombre y al actor ya verificado. La prueba de actor no sirve como prueba de contacto. No se acepta un perfil desde body/header HTTP.
- `identity.invite_verified_local_email`: exige la operación MEMBERSHIP_INVITE autorizada, owner/MFA y contexto actual; deriva el emisor del vínculo del actor, sin issuer de entrada HTTP; resuelve exactamente un destinatario, fija subject y llama al `identity.create_invitation` original.
- Trigger `invitation_recipient_unique` antes de INSERT: bloqueo por tenant/issuer/subject y rechazo de duplicado pendiente no vencido. Protege también la ruta original, sin reescribir invitaciones históricas ni impedir una nueva tras caducidad/revocación.

Funciones definer con search_path fijo, objetos calificados y EXECUTE PUBLIC revocado. Ningún SQL genérico, rol nuevo, BYPASSRLS o privilegio administrativo runtime. Guardas verifican FORCE/ownership/privilegios privados y trigger habilitado. No es defensa contra DBA o BFF/clave de atestación comprometidos; la custodia/rotación mantiene los gates existentes.

La actualización de contactos y su resolución se serializan por emisor local mediante advisory lock, tras validar la atestación/capacidad. El recibo se bloquea por tenant/command y la inserción por receptor. No se invierte el orden de raíz/membresía/cuota V9 ni se toma un upgrade tardío de raíz. Los contactos se bloquean durante la resolución; un cambio posterior no retargetea issuer/subject.

## API y garantías

- GET `/api/v1/tenants/{tenantId}/team/invitation-options`: owner+MFA y MEMBERSHIP_INVITE; devuelve sólo disponibilidad del flujo local y delivery=MANUAL. No es un listado de personas o invitaciones.
- POST `/api/v1/tenants/{tenantId}/team/invitations`: CSRF, Idempotency-Key UUID, DTO cerrado email/role; roles posibles supervisor/contador/auxiliar, nunca owner. 201 primera creación; 200 replay con token NULL. No acepta actor, subject, issuer o permisos desde React.
- Aceptación y revocación conservan los endpoints `/api/v1/auth/invitations/accept` y `/revoke`. Una revocación idempotente procesa la solicitud si sigue pendiente; no borra una membresía aceptada. La UI no afirma que una invitación ya aceptada se revocó por recibir 204.

Token aleatorio 32 bytes/base64url, sólo SHA-256 en la tabla original, vigencia 24h, una aceptación atómica y exacta de issuer/subject. El token no aparece en opciones, recibos, logs, journal, outbox, URL ni screenshots. Un replay autorizado devuelve sólo metadatos originales y no consulta el estado actual como un directorio; no recupera el secreto ni promete vigencia presente. Clave reutilizada con otro actor/huella o duplicado vivo: 409. Destinatario no elegible: VALIDATION_FAILED 422 sin detalles; ausencia de sesión 401 y autoridad insuficiente 403.

El token se guarda sólo en memoria del componente después de una respuesta real. Se retira al confirmar que se guardó, ocultar/cambiar la ventana, navegar/desmontar/cambiar de tenant y antes de revocar. No se introduce en TanStack Query, localStorage ni sessionStorage. Respuestas tardías no publican token/notificación en otro contexto. Si se pierde la primera respuesta, reintentar no genera otra invitación ni recupera el token: revocar la solicitud y emitir una nueva cuando corresponda.

Copiar al portapapeles requiere un clic explícito y confirmación del navegador; entonces la vista retira el secreto. El portapapeles pertenece al usuario/sistema operativo, no es almacenamiento gestionado por la aplicación. No se copia automáticamente ni se registran token o errores del portapapeles en logs/notificaciones.

## Cuotas, auditoría y entrega

No se duplica el sistema de invitaciones ni se altera la regla aprobada V9: pendientes no consumen/reservan USERS; la aceptación original comprueba cuota vigente y serializa la última plaza en PostgreSQL. La UI ofrece consultar Plan y uso mediante la pantalla existente y aclara que el cupo se valida al aceptar. No promete una plaza por emitir una invitación.

Invitación + INVITATION_ISSUED + outbox + recibo participan en la misma transacción PostgreSQL. Un fallo del journal/outbox revierte toda la creación. Actor proviene de la sesión, correlation ID del servidor; no se incorporan correo, nombre o token al evento/payload. Aceptación y revocación conservan sus eventos reales. No hay worker ni envío externo.

La UI dice **entrega manual pendiente** y permite ocultar el token de un uso. La persona propietaria utiliza un canal interno autorizado y el destinatario lo introduce después de iniciar sesión OIDC. No se crea un enlace con token ni se afirma que se envió correo. El proveedor/canal productivo, semántica de verificación y reglas de entrega requieren aprobación futura OQ-014.

## Pruebas y reproducción

`EmailInvitationTests`: 27 casos HTTP/SQL/OIDC reales, positivos sin grants sintéticos; destinatario verificado/exacto, cambio de email sin retarget, desconocido/no verificado/ambiguo, MFA ausente/vencido, otros roles/tenants, CSRF/DTO, token expirado/revocado/reutilizado, replay sin secreto, dos comandos/replays concurrentes, cuotas reales en aceptación, fallos de sinks, privacidad de logs/sesiones y oráculos de guardas. `EmailInvitationMigrationIT`: upgrade V9, checksums, ejecución única y rollback/reintento de V10.

Desde raíz, Docker Linux y herramientas existentes: Maven Wrapper `clean verify`, `node tools/ci/reports.mjs backend`; npm ci web/tools, typecheck/lint/format/Vitest/build/Playwright, `node tools/ci/auth-browser.mjs`, OpenAPI y generación TypeScript, conformidad HTTP, Gitleaks/actionlint/zizmor/Trivy dev/test y allowlist de artifacts. No ejecutar auth-browser test-compile y Maven clean simultáneamente.

Mínimos: **416 Java (375 Surefire+41 Failsafe),107 Vitest,61 Playwright fundación/presentación,18 OIDC y21 tools/ci**. Los resultados finales y cinco checks se registran por SHA en el PR; esta especificación no convierte escenarios pendientes en PASS. Sólo datos sintéticos y Keycloak local en los ejemplos/evidencias.
