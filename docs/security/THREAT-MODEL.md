# Modelo inicial de amenazas
**Activos:** datos fiscales, contribuyentes, honorarios, documentos, sesiones, llaves futuras, backups. **Actores:** usuario tenant malicioso, empleado comprometido, soporte interno, bot, dependencia comprometida, XML/PDF/ZIP malicioso.
| Amenaza | Mitigación | Evidencia |
|---|---|---|
| IDOR entre empresas | autorización+RLS | tests API/DB/jobs con A y B |
| suplantación/cookies | OIDC, MFA, CSRF, sesión segura | pruebas auth |
| documentos accesibles por URL | signed URL después de autorización | tests de descarga |
| abuso archivo XML/ZIP | parser seguro, límites y streaming | fixtures hostiles |
| XSS/SSRF | encoding, CSP, filtrado destinos | pruebas adversariales |
| secretos expuestos | gestor de secretos, no llaves en MVP | secret scan y logging |
| exportaciones masivas | permisos, rate limits, auditoría | pruebas reporte |
| pérdida/restauración | backup cifrado, restore aislado | simulacro |
| uso SAT no autorizado | ADR de llaves + consentimiento | aprobación previa |
Actualización por cada nuevo módulo y revisión antes del piloto. No reemplaza auditoría independiente.

## Revisión #26 — infraestructura, sin autenticación todavía

| Riesgo | Control implementado / evidencia | Riesgo pendiente |
|---|---|---|
| WHERE tenant omitido; lectura/escritura cruzada | PostgreSQL FORCE RLS, cerca RESTRICTIVE USING/WITH CHECK, FK/unique tenant-first; TenantRlsTests con runtime real | Nuevas tablas requieren misma política y pruebas; #30/API/archivos/jobs pendientes |
| GUC falsificado, borrado o heredado del pool | Vínculo privado tenant+xid, SET LOCAL parametrizado, pool PID idéntico tras commit/rollback y cuatro conexiones concurrentes | Código/SQL con credenciales runtime puede elegir contexto al inicio; #27/#28 deben autorizarlo |
| Cambio de tenant anidado / rollback parcial | REQUIRED mismo tenant; cambio aborta operación, NESTED deshabilitado; postcondición y pruebas reales | No permitir SQL manual de control transaccional ni REQUIRES_NEW para cambiar despacho |
| Rol privilegiado / drift RLS | Guardas de ownership, roles/membresías, FORCE/cerca y grants; pruebas negativas de ALTER/grants/políticas | Migrador/DBA conserva capacidad DDL: secretos separados, revisión humana y monitoreo futuro |
| Canarios SQL en logs de PostgreSQL | LOG/terse, sin ERROR/DETAIL/sentencias/parámetros; prueba de conversión real y denegación de log_statement al runtime | Menos detalle de diagnóstico SQL; logs administrativos/catastróficos bajo custodia restringida |
| Metadatos de contexto retenidos / contención | Schema privado, vínculo invalidado al fin de tx, limpieza acotada SKIP LOCKED, tests concurrentes | Medir carga/bloat/autovacuum antes del piloto; no es un historial/auditoría |

Modelo efectivo y límites en [tenant-foundation-26.md](../data-model/tenant-foundation-26.md). No hay datos reales, proveedor OIDC, membresías definitivas, capacidades, break-glass ni autorización fiscal implementados en #26.

## Antecedente de diseño #27 — previo a autorización e implementación

| Amenaza | Diseño sometido a aprobación | Verificación pendiente |
|---|---|---|
| Token/issuer/nonce/state falsificado | OIDC Code+PKCE mediante Spring Security, issuer/audience/azp/firma/tiempos y redirects exactos | Intercambio HTTP real con IdP local; negativas observables sin sesión |
| CSRF, login CSRF y fijación de sesión | BFF mismo origen, cookie HttpOnly/Secure/Lax, CSRF y rotación de ID | Navegador real, cookies, mutaciones, expiración y logout |
| Bootstrap de identidad permite leer otros despachos | Actor verificado+xid privado, SELECT propio bootstrap y autorización de membresía antes de TenantTransactions | Forja de actor/GUC, ausencia de contexto, SQL sin filtros, catálogo/grants y pool |
| Roles/MFA falsos o sesión con permisos obsoletos | Roles internos, política MFA explícita del issuer, deny privilegiado sin evidencia; releer usuario/membresía | Revocación concurrente, claims sin semántica válida, MFA vencido y múltiples sesiones |
| Robo/reuso de invitación y vinculación por correo | Hash de token, consumo atómico, expiración y receptor verificado; no autolink por email | Dos aceptaciones simultáneas, destinatario distinto, caducidad/revocación y rollback |
| Robo de sesiones desde una inyección SQL de negocio | Schema/datasource/rol mínimo de sesiones separado y principal sin tokens | Catálogo de privilegios, ausencia de tokens en DB/logs/artifacts y JAR sin fixture |

La [propuesta #27](../decisions/issue-27-oidc-session-proposal.md) registra decisiones pendientes y límites. Esta tabla no acredita mitigaciones nuevas ni sustituye las pruebas de seguridad o la selección/contrato del proveedor. No se han cambiado RLS, perfiles ni permisos reales en esta fase.

## Implementación #27 posterior a la autorización técnica del propietario

La tabla anterior es el antecedente del diseño, no el estado actual. [Guía efectiva](AUTHENTICATION.md) y [evidencia](../../backend/VALIDATION-27.md) documentan los controles implementados: firma/issuer/audience/expiración y nonce/state/PKCE reales con Keycloak; sesión mínima privada y sin tokens; CSRF/rotación/expiraciones/logout; actor HMAC+xid inmutable; RLS de lectura propia; membresía ACTIVE antes del contexto y función SQL runtime protegida; MFA reciente y rol interno para privilegios; invitación exacta atómica/hasheada con revocación.

Ataques negativos incluyen UUID/headers/SQL de otro actor, ausencia de contexto, otro tenant, pool reutilizado, concurrencia, aceptación doble, usuario deshabilitado, MFA ausente/futuro/vencido y canarios en logs/respuestas. Las credenciales de sesión no tienen USAGE de datos de negocio; runtime no accede a sesiones/clave HMAC. El BFF/migrador y la cuenta de sesiones son fronteras de confianza: su compromiso no queda resuelto por RLS. Rotación/custodia productiva, carga/bloat/DoS, proveedor/MFA/recuperación/revocación federada, capacidades y expedientes permanecen pendientes. No hay proveedor de producción ni aceptación tácita de claims MFA arbitrarios.

## Base #28: IDOR dentro del despacho

| Amenaza | Control/evidencia | Límite |
|---|---|---|
| UUID conocido de cartera ajena, rol/permit falsificado | Actor de sesión verificada, rol SQL, capacidad aprobada y asignación exacta; HTTP 404 y filas protegidas | Matriz fiscal/OQ-015 sigue DENY hasta aprobación |
| WHERE de tenant o asignación omitido | Cerca RLS restrictiva de tenant y recurso en fixtures; list/count/page/export/download bajo misma operación | Cada futura tabla debe incorporar ambas cercas y pruebas; #74 no se anticipa |
| Revocación entre check y uso | FOR SHARE usuario/membresía/rol/política/asignación hasta commit; pruebas con bloqueos PostgreSQL observados | Revocación posterior se ordena después de una operación ya autorizada, no cancela commits |
| Lectura utilizada para editar/exportar | Capacidad por operación y políticas SQL por comando con WITH CHECK | No hay wildcard, owner universal ni herencia supervisor |
| Scope/GUC/contexto retenido o cambiado | Actor+operación+xid privados, inmutabilidad, callback temporal, pool/concurrencia y rollback | BFF/DBA comprometidos no están contenidos por RLS |
| Logs de denegación revelan PII/reglas | Evento tipado cerrado y formatter por allowlist, RFC 9457 fijo/correlación | Persistencia audit/outbox #29 y control de carga/bloat pendientes |

[Diseño físico y guía](RESOURCE-AUTHORIZATION.md). No se implementan políticas fiscales, reportes reales ni autorización documental productiva por usar esos nombres en fixtures sintéticos.

## Base #29 — integridad durable

| Amenaza | Control/evidencia | Límite pendiente |
|---|---|---|
| Cambio confirmado sin journal/outbox; falso éxito tras rollback | Triggers de cambios reales y escritura en misma transacción; fallo de INSERT revierte todo | Nuevos módulos deben integrar el puerto y pruebas; no hay dominio fiscal |
| Borrado/modificación de historial o exposición a owner | Sin DML/SELECT runtime, FORCE RLS, triggers append-only, AUDIT_VIEW denegado, oráculos de grants/cerca/trigger | No WORM ni protección frente a DDL de DBA/migrador; retención/custodia pendientes |
| Actor/correlación/payload forjados | Actor OIDC ligado a xid, HMAC de comando separado, DTOs/columnas cerradas, JSON outbox sólo UUID de evento | BFF/clave comprometidos exceden frontera; rotación productiva pendiente |
| Capturar denegación permite escribir en tenant ajeno | Revalidación después de rollback, prueba específica, journal global sin tenant ni recurso, sin endpoint arbitrario | Si persiste falla, se mantiene DENY y diagnóstico; tasa/alertas/retención antes del piloto |
| Crash entre commit y dispatch; doble claim/ack obsoleto | Outbox SQL, SKIP LOCKED, lease token/attempts, fencing y recibo de consumidor idempotente | At-least-once: efecto externo necesita deduplicación downstream |
| Tenant del payload se usa como autoridad de negocio | Cuenta de transporte con scope explícito por OID, sin app/identity, sin cuentas por defecto ni scheduler | Servicio de negocio y política de revalidación deben aprobarse; no owner ficticio |
| Datos sensibles en cola/logs/artifacts | Payload sólo auditEventId, metadata cerrada, canarios, allowlists de log/artifacts | Revisión humana y límites de carga/bloat siguen siendo necesarios |

[Journal](AUDIT-JOURNAL.md), [outbox/runbook](../delivery/OUTBOX.md), [pruebas](../../backend/VALIDATION-29.md). #30 continúa independiente; #74 no se modifica.

## Regresión transversal #30

La [matriz ejecutable y cobertura](CROSS-TENANT-REGRESSION.md) conecta amenaza, frontera, control, ataque, expectativa y métodos exactos. A/B/C, carteras separadas, claims/cookies/UUID/GUC falsos, revocaciones concurrentes, pool físico/threads, journal y leases se comprueban con HTTP real/Keycloak/PostgreSQL. [Mutaciones](NEGATIVE-TESTING.md) detectan retirada de cercas, FORCE, BYPASSRLS, grants, search_path, chequeos ACTIVE y MFA. No se promete inmunidad frente a DBA/IdP/BFF comprometidos. Futuros caches/archivos/autoridad de jobs y decisiones #74/OQ-015/producción aparecen como NOT VERIFIED, nunca como checks verdes. No se modifica el modelo productivo V1–V5.
