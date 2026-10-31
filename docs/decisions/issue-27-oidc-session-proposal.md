# #27 — propuesta de identidad, BFF y sesiones para aprobación

**Antecedente histórico: propuesta presentada antes de la autorización técnica del propietario el 2026-10-30.** Fecha: 2026-10-30. Base auditada: main `05b5b18b157cd88d8f76967f9d2d3b179960f7aa`, PR #82 integrado. Referencias: FR-002/003/005, BR-003/004/005, NFR-002/003/007/008, ADR-005, ADR-007, OQ-014/022, issues #27/#28/#33 y gate #74.

Este documento conserva la evaluación inicial y sus supuestos, no el inventario efectivo actual. El propietario autorizó después BFF/OIDC/PKCE, JDBC privado, Keycloak local, identidad global, membresías/roles, MFA verificable, bootstrap controlado e invitaciones exactas. La [guía implementada](../security/AUTHENTICATION.md) y evidencia #27 describen código/pruebas actuales y diferencias. Proveedor/MFA/operación de producción siguen pendientes; ADR-005 sigue Proposed y OQ-014 Open.

## 1. Resultado de la auditoría

- Main y worktree inicial coincidían con el squash de #26 y estaban limpios. RLS ya existe; V1/V2 quedan intactas en esta fase.
- No hay Spring Security ni OAuth2 Login configurados en el código productivo. Sólo existe el módulo platform; `/api/v1/me` y login siguen sin implementar.
- `TenantTransactions` no autoriza el UUID recibido: exige un adaptador confiable anterior. Establece un solo tenant por transacción PostgreSQL, sin read-only/NESTED, y participa en una exterior REQUIRED.
- Las guardas rechazan tablas de app sin tenant_id/RLS, acceso runtime a infrastructure y privilegios DB elevados. Identidades/sesiones globales necesitan namespaces y permisos explícitos; no se puede añadir una tabla global a app y relajar la guarda genéricamente.
- `SafeProblems` y el acceso a correlación son internos de platform. Un módulo identity no debe importar esos internals; hará falta un puerto público mínimo para respuestas seguras desde filtros.
- El cliente React usa `credentials: omit` y `redirect: error`; sólo conoce public/info. Router tiene bienvenida/estados de desarrollo, no navegación autenticada.
- OpenAPI implementado 1.0.0 contiene sólo public/info; la respuesta 401 preparada exige WWW-Authenticate: Bearer. El modelo BFF necesita revisar esa convención/challenge y contratos, sin fingir que un token Bearer será aceptado.
- Observabilidad ya usa proyección cerrada de logs/rutas/labels. Nuevas rutas de autenticación requieren inventario de plantillas; no añadir issuer, subject, usuario, tenant, sesión o tokens como labels.
- Las 135 pruebas backend, 21 Vitest y 13 Playwright actuales son la base de regresión, no pruebas de autenticación. No se sustituirán por filtros ficticios.

## 2. Evaluación de proveedores

Consulta de fuentes oficiales: 2026-10-30. Precios USD, sin impuestos, tipo de cambio ni compromiso contractual. Una persona con varias membresías no debe convertirse en varias identidades por despacho. Se propone un emisor/proyecto por entorno, con membresías en Fiscuvia; usar Organizations/tenants del proveedor sólo si existe una necesidad y costo aprobados.

| Alternativa | MFA, usuarios y recuperación | Sesiones/revocación | Operación y portabilidad | Costos y compatibilidad |
|---|---|---|---|---|
| Auth0 administrado | Login hospedado, gestión de usuarios y opciones MFA/recuperación; verificar factores y plan contratado | Separar logout aplicativo, SSO y proveedor upstream; integrar capacidades verificadas | Menos operación del IdP; dependencia de configuración, plan y APIs de administración. Protocolo OIDC reduce acoplamiento del login | La página muestra Free hasta 25,000 MAU y Essentials desde USD35/500 en su vista B2C. **No es una cotización B2B/MFA**: confirmar Organizations, SSO, factores y precio para 1,000/10,000 MAU antes de elegir. Candidato directo para cliente OIDC estándar |
| Firebase Authentication + Identity Platform | Gestión/recuperación administradas; MFA y federación dependen de producto/configuración | Sus tokens y APIs de revocación tienen semántica propia que habría que integrar con la sesión BFF | El flujo web documentado utiliza SDK Firebase para federar con OIDC; no demuestra por sí mismo un issuer genérico Authorization Code para oauth2Login | Tarifas Identity Platform: tier 1 hasta 50,000 MAU sin cargo MAU; federación OIDC/SAML tier 2, 50 gratis y USD0.015/MAU posterior. SMS/MFA se cobra aparte. Necesita spike de adaptación BFF; no copiar la integración Bearer de TrailerSaaS |
| Keycloak autoalojado | OIDC, OTP/WebAuthn, recuperación y administración bajo control propio | Logout OIDC, administración de sesiones y políticas locales; validar configuración real | Mayor responsabilidad de parches, HA, DB, backups, SMTP, recuperación y vigilancia. Portabilidad de protocolo buena; realm/configuración también requieren mantenimiento | Sin tarifa de licencia por MAU del software comunitario; infraestructura y operación no son gratuitas. Adecuado como IdP real exclusivamente local de pruebas; no implica elegirlo para producción |

Fuentes: [precios Auth0](https://auth0.com/pricing), [MFA Auth0](https://auth0.com/docs/secure/multi-factor-authentication), [capas de logout](https://auth0.com/docs/authenticate/login/logout), [precios Identity Platform](https://cloud.google.com/identity-platform/pricing), [facturación por método de autenticación](https://docs.cloud.google.com/identity-platform/docs/multi-tenancy-billing), [federación OIDC Firebase Web](https://firebase.google.com/docs/auth/web/openid-connect), [revocación Firebase](https://firebase.google.com/docs/auth/admin/manage-sessions), [OIDC Keycloak](https://www.keycloak.org/securing-apps/oidc-layers) y [administración Keycloak](https://www.keycloak.org/docs/latest/server_admin/index.html).

Escenario ilustrativo, **no presupuesto aprobado**: 100 despachos × 10 personas distintas = 1,000 MAU. Identity Platform tier 2 daría `(1000-50)*0.015 = USD14.25/mes` de MAU, o USD149.25 para 10,000 MAU; el tier depende del proveedor de autenticación upstream, no de que nuestro backend use OIDC. SMS de México figura a USD0.04/mensaje, con cuota diaria gratuita: 4,000 mensajes serían hasta USD160 antes de esa cuota. No sumar esto como costo total del BFF ni asumir que incluye soporte, correo o todas las funciones MFA. Para Keycloak, presupuesto = hosting + horas de operación + correo/backups/HA; ejemplo puramente supuesto USD40 + 4h×USD25/h = USD140/mes, sin dimensionamiento ni cotización. Auth0 B2B/MFA queda por cotizar: no extrapolar USD35 a ese escenario.

**Preferencia propuesta:** BFF OIDC estándar, con un IdP real local de pruebas y sin SDK de proveedor en React. Para producción favorecer un administrado OIDC si el plan MFA/recuperación, privacidad y costo resultan aceptables; Auth0 es candidato, no elección aprobada. Keycloak de producción requiere aceptar explícitamente su operación. Firebase necesita justificar una adaptación adicional y su portabilidad. No crear cuentas, contratar planes ni consumir servicios externos en este PR.

## 3. Arquitectura propuesta del BFF

React y backend bajo el mismo origen. Spring Security oauth2Login como cliente confidencial: Authorization Code + PKCE S256 explícito, state y nonce por flujo, redirecciones exactas y return-to local permitido. Verificar firma/algoritmo permitido/JWKS, issuer exacto, audience/azp, exp/iat, nonce y claims necesarios mediante biblioteca; no reimplementar OAuth ni aceptar tokens/roles de headers arbitrarios.

El BOM local Boot 4.1.2 administra Spring Security **7.1.2** y Spring Session **4.1.1**. Candidatos: starter-security-oauth2-client, starter-security y starter-session-jdbc, con tests correspondientes administrados. Todavía no se han añadido ni verificado mediante login real en Java 25: la compatibilidad ejecutable es parte de la fase de implementación. Referencias: [OAuth2 Login Spring](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html), [PKCE](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html), [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).

Se propone Spring Session JDBC sobre PostgreSQL existente, con schema privado y datasource/rol de sesiones separado y mínimo. No agregar Redis. Sus tablas son globales de infraestructura, nunca expedientes; el rol runtime de negocio no debe poder enumerarlas. Migraciones explícitas, sin init automático del framework. Esta separación implica nuevas credenciales locales sintéticas/grants y requiere aprobación del modelo antes de escribir DDL.

Cookie `__Host-FiscuviaSession`: HttpOnly, Secure, Path=/, sin Domain, SameSite=Lax para callback GET OIDC. Sin session ID en URL, remember-me ni tokens en storage web. Regenerar ID al autenticar; CSRF de Spring en login/logout/invitación/mutaciones. Token CSRF en respuesta dedicada y memoria del cliente; no confundirlo con identidad/autorización. Comprobar Origin/redirect permitidos y desactivar CORS amplio. HTTPS local con certificados efímeros/trust de proceso de prueba; nunca instalar certificados globales ni deshabilitar TLS del JAR para los tests.

Propuesta de tiempos para revisión: 15 min de inactividad, máximo absoluto 8 h y reautenticación periódica máxima 30 min; MFA reciente ≤10 min para acciones privilegiadas. No confundir expiración del ID token con duración de la sesión aplicativo: deberán documentarse y probarse separadamente. No prometer revocación instantánea del proveedor sin back-channel/evento confiable o comprobación admitida por él.

Persistir sólo principal interno mínimo, versión de autenticación, fechas y evaluación MFA confiable; no serializar por defecto OidcUser, el ID token, access/refresh tokens ni todos los claims. No se requieren APIs OAuth de terceros en #27. Logout local invalida sesión y cookie mediante POST+CSRF. Logout con el proveedor usa descubrimiento/redirects aprobados y sus capacidades; comprobar por separado ambos resultados, sin guardar un ID token sólo para fingir logout global.

## 4. Identidad y bootstrap de membresías sin dependencia circular

Separación propuesta de namespaces: `identity_private` para identidades globales/contexto de actor sin SELECT runtime; `identity` para relaciones de membresía tenant-scoped con RLS; `sessions` privado para Spring Session y su rol separado. Nada cambia la cerca de datos de app ni concede BYPASSRLS, ownership, privilegios DBA o consultas genéricas. Los nombres y DDL son propuestos, no migraciones aplicadas.

Modelo mínimo propuesto:

| Relación | Claves y restricciones previstas |
|---|---|
| usuarios internos | UUID global; ACTIVE/DISABLED y auth_version; conservar autoría |
| identidades externas | UUID, user_id FK, issuer+subject UNIQUE; no usar email como PK ni vincular cuentas automáticamente por correo |
| membresías | tenant_id NOT NULL FK a app.tenants, id UUID, user_id FK, ACTIVE/SUSPENDED/REVOKED; PK `(tenant_id,id)`, UNIQUE `(tenant_id,user_id)` |
| roles iniciales | `(tenant_id,membership_id,role_code)`, FK compuesta; conjunto cerrado de cuatro roles, sin capacidades/expedientes todavía |
| invitaciones | tenant_id, UUID, receptor y condición de identidad aprobada, hash de token aleatorio de 32 bytes, expiración, consumo/revocación/autoría; sin token bruto persistido |
| sesiones | modelo de infraestructura Spring Session, índice de principal y atributos mínimos; sin datos de despacho/tokens OAuth |

Flujo propuesto, antes de cualquier `TenantTransactions.execute`:

1. Spring verifica el intercambio OIDC. Un adaptador interno obtiene issuer/subject del resultado verificado o del principal de sesión; nunca del body/header/query.
2. Una función SQL **estrecha**, fija y parametrizada resuelve exactamente ese par a usuario activo y auth_version. Vincula un actor inmutable al xid en un registro privado, con las mismas precauciones de #26. No recibe SQL, nombres de tabla, filtros genéricos ni roles. SECURITY DEFINER limitado a metadatos de identidad, search_path seguro, sin ownership/bypass runtime y sin lectura de expedientes.
3. Las políticas SELECT de membresía permiten bootstrap **sólo de las filas del actor verificado** cuando no hay tenant; no habilitan INSERT/UPDATE/DELETE en modo bootstrap. Una cerca separa lectura propia bootstrap y operaciones con tenant autorizado. Probar catálogo/privilegios y suplantación de actor/GUC, no añadir una política permisiva global para resolver el ciclo.
4. Dentro de la misma transacción exterior read-write, releer y bloquear la membresía solicitada, verificar ACTIVE, rol y MFA cuando corresponda. El UUID del despacho es únicamente un recurso solicitado. Si no pertenece al actor, denegar sin establecer contexto.
5. Sólo entonces ejecutar TenantTransactions, que participa en esa transacción y conexión. Después quedan las comprobaciones de capacidad/expediente de #28; el rol no las sustituye.

No existe endpoint para establecer contexto RLS genérico. El selector React muestra únicamente membresías reales del backend; cada operación lleva su despacho solicitado y se autoriza nuevamente. Evitar un tenant global mutable de sesión que mezcle pestañas o peticiones concurrentes. La raíz actual sólo tiene UUID/fecha: no inventar nombres de despachos para la UI.

Revisar estado/versión usuario y membresía en cada petición/operación; no tomar roles ni membership como autoridad desde la sesión o JWT. Suspender/revocar impide operaciones posteriores al commit; una operación ya autorizada mantiene su frontera transaccional/locks. Invalidación global de usuario elimina/invalida sus sesiones; revocar una membresía no debe conceder acceso ni revocar indebidamente sus otros despachos. Documentar esta semántica concurrente y probarla.

**Límite de confianza:** como en #26, código/credenciales DB runtime comprometidos no se convierten en un sandbox sólo con contextos SQL. El adaptador de autenticación y sus llamadas internas son una frontera de seguridad que exige revisión. No exponer issuer/subject/userId como prueba de autorización de un endpoint.

## 5. Roles, MFA, invitación y recuperación

Propuesta inicial: FIRM_OWNER_ADMIN, FIRM_SUPERVISOR, ACCOUNTANT y ASSISTANT. Son asignaciones internas por membresía, sin aceptar roles del IdP ni adoptar la matriz completa Proposed. Owner/admin y supervisor se consideran privilegiados para este corte. Sin política MFA del issuer aprobada, negar acceso privilegiado; no usar presencia de amr/acr como aprobación. El adaptador deberá comprobar issuer/firma, significado del valor según política registrada, factores/configuración y auth_time reciente; valores desconocidos o ausencia de evidencia => deny.

No implementar recuperación de contraseñas/MFA en Fiscuvia. Usar el proveedor seleccionado y documentar revocación posterior. El IdP de pruebas tendrá política real controlada y casos que emiten claims insuficientes/incorrectos; ese perfil y sus claves no deben estar en el JAR productivo.

Frontera propuesta de invitaciones #27: emisión/aceptación/revocación mínimas y suspensión, con owner/admin activo+MFA, DTO seguro y sin directorio/administración completos #33. Para aceptar, exigir sesión OIDC válida, CSRF, token de un solo uso no expirado/revocado y vínculo con el destinatario. Usar emisor permitido y correo verificado sólo cuando un perfil de proveedor aprobado garantice esa semántica; de otro modo bloquear aceptación o usar una invitación ligada al issuer/subject exactos, previa aprobación de esa modalidad. No asumir email_verified confiable por existir, no vincular cuentas por email y no crear membresía antes de completar la verificación atómica. Nuevo usuario sin invitación válida no adquiere acceso por autenticarse.

La política de receptor y bootstrap del primer owner requiere decisión explícita. Se propone aprovisionamiento interno controlado con identidad exacta y MFA verificado; no endpoint público de alta de owner, usuario inicial con password fijo ni cuenta admin sembrada. Invitar/retirar owners y proteger al último owner requieren el alcance aprobado de #33; no inventar una regla irreversible aquí.

## 6. API/UI propuestas, no implementadas

- `/api/v1/auth/*`: iniciar flujo, callback, CSRF y logout local; URI y métodos exactos se fijarán en OpenAPI antes del código. No un endpoint que acepte tokens/userId como autenticación.
- `GET /api/v1/me`: DTO mínimo con identidad interna y membresías autorizadas; no tokens, claims crudos ni datos de otros usuarios. Desconocido/inactivo => respuesta segura; no directorio de búsqueda de personas.
- Invitación/aceptación/suspensión: contratos limitados a la frontera anterior, con errores de expiración/uso/revocación que no permitan enumerar destinatarios. Token bruto sólo una vez al emitir, hash en DB; no logs, métricas, URLs de API ni artifacts con el token.
- Puertos públicos mínimos desde platform para Problem Details/correlación en filtros; identity declara dependencia de platform. Nada de importar internals ni cycles.
- React: acceso navy/teal/Inter con botón al IdP real, sin formulario de contraseña propio ni login simulado. Estado cargando/error/expirada/sin acceso, logout y selección de membresía real. Fetch same-origin con cookies para rutas autenticadas; ningún secreto VITE_, token en localStorage o dashboard fiscal operativo ficticio.
- Capturas reales 1440×900, 390×844 y error/acceso denegado después de implementar. Imagen conceptual original sigue ausente; comparar con especificaciones documentadas, sin afirmar comparación exacta.

## 7. Plan de validación obligatorio de la implementación

IdP real exclusivamente local (propuesta: Keycloak en Testcontainers, versión/digest a fijar tras aprobación), credenciales/llaves efímeras sintéticas, endpoints OAuth reales y navegador. No filtros de autenticación ficticios ni secretos/realm de pruebas dentro del JAR. Negativas controladas de claims/JWKS deben observar el rechazo HTTP y ausencia de sesión, no sólo llamadas a un verificador.

Matriz: login válido; firma/issuer/audience/azp/exp incorrectos; state/nonce/PKCE manipulados; CSRF/login CSRF; cookies/SameSite/HttpOnly/Secure; session fixation, expiración y logout; usuario desconocido; membresía suspendida/revocada y rol manipulado; MFA sin política/insuficiente/vencido; invitación expirada/usada/revocada/destinatario distinto y dos aceptaciones concurrentes; rollback de identidad+membresía; multi-despacho, actor/UUID ajeno y mismo RFC DEMO; pool, sesiones concurrentes y revocación durante operación; catálogos RLS/grants y SQL bootstrap; canarios en logs/spans/métricas/redirects; JAR sin issuer/claves/fixtures locales. Aumentar mínimos CI, conservar las 135 pruebas backend anteriores y todas las suites web/contratos/seguridad.

Esta fase documental puede ejecutar esas suites **existentes**, pero no acredita ninguno de los escenarios nuevos. No hay capturas de login ni pruebas de OIDC entregadas todavía.

## 8. Aprobaciones concretas solicitadas antes de continuar

1. **Base técnica:** BFF del mismo origen con Spring Security OIDC estándar y PKCE, Spring Session JDBC/rol privado, cookies/CSRF y tiempos propuestos; IdP real sólo local para pruebas. El proveedor de producción permanece sin seleccionar.
2. **Frontera SQL:** identidad global privada, membresías tenant-scoped con bootstrap SELECT limitado al actor verificado y contexto actor+xid; ningún bypass/consulta genérica. Revisar el modelo lógico antes de emitir nuevas migraciones.
3. **Política mínima:** cuatro roles indicados, MFA para owner/admin y supervisor con deny por defecto; modalidad de destinatario de invitación y bootstrap interno del primer owner. Recomendación: prueba local con identidad exacta issuer/subject; invitación por email de producción sólo tras aprobar perfil de proveedor/semántica de verificación.

La pausa obedece al punto 13 de la instrucción del propietario (presentar propuesta y detenerse si el modelo requiere aprobación), al dueño pendiente explícito de ADR-005 y a OQ-014 sobre proveedor/tipo de sesión/MFA/recuperación. El proveedor podría aplazarse técnicamente; lo que se solicita aquí es aprobar las decisiones de sesión, identidad/SQL y política inicial, no contratar un proveedor ni aceptar todos los ADR.

Después de esa aprobación se continuará en la misma rama/PR de #27, revalidando main. No mergear este Draft documental como si cumpliera el issue. No cambiar Accepted/Resolved, abrir otro issue ni continuar #28 automáticamente.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por facturación/presupuesto; seguimiento #79.** Presupuesto $0 intacto; sin cambios de workflows, bypasses o servicios facturables.
