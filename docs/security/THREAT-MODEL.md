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

## Análisis propuesto #27 — controles todavía no implementados

| Amenaza | Diseño sometido a aprobación | Verificación pendiente |
|---|---|---|
| Token/issuer/nonce/state falsificado | OIDC Code+PKCE mediante Spring Security, issuer/audience/azp/firma/tiempos y redirects exactos | Intercambio HTTP real con IdP local; negativas observables sin sesión |
| CSRF, login CSRF y fijación de sesión | BFF mismo origen, cookie HttpOnly/Secure/Lax, CSRF y rotación de ID | Navegador real, cookies, mutaciones, expiración y logout |
| Bootstrap de identidad permite leer otros despachos | Actor verificado+xid privado, SELECT propio bootstrap y autorización de membresía antes de TenantTransactions | Forja de actor/GUC, ausencia de contexto, SQL sin filtros, catálogo/grants y pool |
| Roles/MFA falsos o sesión con permisos obsoletos | Roles internos, política MFA explícita del issuer, deny privilegiado sin evidencia; releer usuario/membresía | Revocación concurrente, claims sin semántica válida, MFA vencido y múltiples sesiones |
| Robo/reuso de invitación y vinculación por correo | Hash de token, consumo atómico, expiración y receptor verificado; no autolink por email | Dos aceptaciones simultáneas, destinatario distinto, caducidad/revocación y rollback |
| Robo de sesiones desde una inyección SQL de negocio | Schema/datasource/rol mínimo de sesiones separado y principal sin tokens | Catálogo de privilegios, ausencia de tokens en DB/logs/artifacts y JAR sin fixture |

La [propuesta #27](../decisions/issue-27-oidc-session-proposal.md) registra decisiones pendientes y límites. Esta tabla no acredita mitigaciones nuevas ni sustituye las pruebas de seguridad o la selección/contrato del proveedor. No se han cambiado RLS, perfiles ni permisos reales en esta fase.
