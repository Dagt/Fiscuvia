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
