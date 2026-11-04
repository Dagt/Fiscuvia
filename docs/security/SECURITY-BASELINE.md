# Seguridad y privacidad: criterios de liberación

## Provisioning/configuración #31

[Frontera administrativa y SQL](../architecture/TENANT-PROVISIONING.md): alta/estado sólo mediante credencial migradora custodiada y aprobación independiente de identidad ya OIDC-verificada; no hay operador HTTP ni autoservicio. Runtime no lee/modifica metadatos globales directamente. Toda configuración exige sesión/usuario/membresía/estado/capacidad y MFA; la política permanente de edición permanece DENY pendiente de aprobación expresa. Raíz con bloqueo hasta commit, versión e idempotencia; journal/outbox atómicos y sin contacto/nombre en payload. Directorio sólo propio; suspensión no destruye historial ni concede facultades de facturación. No se admite URL/SVG/path de logo; fallback sin almacenamiento improvisado. Guardas y mutaciones desechables comprueban FORCE, grants y el filtro de actor del directorio.
## Clasificación
Público / operación interna / confidencial de despacho / fiscal sensible / credenciales criptográficas (prohibidas en MVP).
## Controles
OIDC confiable, MFA para roles privilegiados, permisos de despacho+recurso, invitaciones revocables, sesiones seguras; TLS, cifrado reposo, gestor de secretos, logs sin PII, CSP, protección CSRF/XSS/SSRF, límites de abuso; archivos privados con hash, acceso firmado autorizado, validación MIME y parser XML/ZIP endurecido. RLS y pruebas cruzadas.
## Operaciones
Auditoría append-only orientada a integridad, backups automáticos cifrados y restauración demostrada; retención/eliminación y datos de soporte controlados. Entornos aislados. Política de respuesta a incidentes, vigilancia de dependencias, escaneo de secretos.
## Legal
Avisos de privacidad, roles responsable/encargado, contratos de tratamiento, derechos de titulares, subprocesadores, retención, exportación y borrado sujetos a revisión legal mexicana antes del piloto con datos reales. No afirmar certificación ni cumplimiento garantizado.
## Gate piloto
Threat model actualizado, revisión independiente adecuada al riesgo, tests tenant/RLS, restauración, configuración OIDC/MFA, revisión legal/contratos, monitoreo y aceptación de seguridad. Sin ello usar sólo datos sintéticos.

## Base verificable #26 (para revisión)

[Tenancy física y contrato transaccional](../data-model/tenant-foundation-26.md): runtime sin ownership/roles DB/privilegios de bypass, FORCE RLS y cerca restrictiva, FKs tenant-first y contexto privado por xid real con GUCs locales. Negación por defecto y pruebas de pool/concurrencia, SQL sin filtros, forja de GUC, checksum/rollback y resistencia a relajación de políticas. Runtime no puede modificar la raíz tenants ni el registro privado. No existe endpoint de selección/diagnóstico tenant; logs/métricas/trazas no reciben el tenant.

RLS no autentica ni autoriza un UUID: #27/#28 deben resolver identidad, membresía, capacidades y recurso antes de llamar el puerto interno. Credenciales runtime comprometidas o código interno malicioso quedan fuera de esa garantía; el migrador/DBA puede alterar DDL y necesita custodia separada. PostgreSQL local/tests suprime errores SQL/sentencias/parámetros que podrían contener valores privados, con prueba canario; no ampliar logging sin revisión. El piloto sigue bloqueado por los gates restantes, no sólo por tests de DB.

## Base de identidad #27 integrada

[Controles implementados y límites](AUTHENTICATION.md): BFF OIDC/PKCE con IdP real sólo local, sesión JDBC privada sin tokens OAuth, CSRF, cookie __Host Secure/HttpOnly/Lax, rotación/inactividad/expiración absoluta y revocación por auth_version. V3 separa globales/sesiones/membresías, protege el bootstrap con HMAC por xid y exige membresía autorizada incluso en la entrada SQL de contexto runtime. Sin BYPASSRLS ni endpoints de registro propietario.

Privilegios administrativos: owner activo y MFA local verificable de los últimos 10 min. Perfiles MFA desconocidos y proveedor productivo no aprobado no conceden privilegios. Invitaciones sólo issuer/subject exactos, hashes, expiración y consumo atómico; sin email autolink. Los secretos de fixtures son efímeros/ignorados, fuera del JAR/artifacts. Workflows conservan permisos mínimos/checks; presupuesto $0 y REMOTE CI NOT VERIFIED (#79). #28, #33, proveedor/MFA/operación productivos y #74 no se declaran completados.

## Base de autorización #28

[Autorización por recurso](RESOURCE-AUTHORIZATION.md): capacidades no configuradas deniegan, sin jerarquía implícita; asignaciones exactas de membresía/capacidad/recurso; contexto SQL privado inmutable por xid; chequeo y uso en la misma transacción con bloqueos contra revocación concurrente. Puertos de negocio no exponen TenantTransactions ni actores/roles HTTP. Tablas futuras requieren RLS tenant **y** recurso antes de listas, búsqueda, reportes, descargas o comandos. Fixtures y endpoints sintéticos no entran al JAR.

V4 no concede permisos fiscales/documentales/reportes: matriz Proposed/OQ-015/#74 siguen bloqueando esos grants. El migrador administra únicamente mediante cambios revisados; runtime no puede leer/escribir grants ni políticas. Decisiones operacionales tienen categorías cerradas y correlación, sin identificadores/PII; no sustituyen auditoría/outbox #29.

## Journal/outbox #29

[Política y modelo físico](AUDIT-JOURNAL.md): eventos de identidad por cambios reales, append-only lógico, FORCE RLS/tenant-first, sin lectura ni DML runtime/sesiones. Éxito sensible, journal y outbox son atómicos; falla al insertar impide commit. Actor/correlación son verificados por servidor y prueba ligada a xid; ningún JSON/actor arbitrario del frontend se convierte en hecho.

Denegaciones privilegiadas atribuibles se escriben después de rollback en un journal global privado sin tenant/recurso solicitado; indisponibilidad/revalidación fallida conserva el rechazo y diagnóstico seguro, sin afirmar persistencia. [Transporte](../delivery/OUTBOX.md) tiene autoridad separada, vacía por defecto, sin permisos de app/identity ni worker productivo. At-least-once/fencing/idempotencia, no exactly-once ni WORM. DBA/migrador/BFF comprometidos superan esta frontera; retención, custodia, lector AUDIT_VIEW y servicio de negocio requieren aprobación posterior.

## Gate transversal #30

Mínimo 295 backend, 37 Vitest, 13 Playwright de fundación, cinco OIDC y 16 tools/ci. [Matriz](CROSS-TENANT-REGRESSION.md) y [mutaciones desechables](NEGATIVE-TESTING.md) obligatorias: no basta con conservar un total sustituyendo un método crítico. Reporte estático sanitizado por SHA, sin datos runtime, publicado sólo tras JUnit completo. Ningún frontend, UUID, rol enviado ni mensaje outbox constituye autoridad. Nuevos módulos de cache/archivos/jobs deben añadir sus contratos negativos antes de habilitarse; #74/OQ-015/proveedor y MFA productivos siguen pendientes. #79 resuelto con CI #85, sin cambiar presupuesto $0; cada PR requiere cinco resultados reales success.
