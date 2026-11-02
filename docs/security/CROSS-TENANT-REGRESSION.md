# Regresión transversal y amenazas — #30

Base auditada: main `f40a4980c1eaf268c16fcc39b2855dbcc71378a6` (#26–29 integrados). #79 cerrado tras CI Ubuntu de #85; presupuesto $0 intacto. Este documento no aprueba #74, OQ-015, proveedor/MFA productivos ni ADR Proposed.

## Matriz previa a nuevas pruebas

La inspección de V1–V5 y de los puertos platform/identity/audit encontró controles implementados y suites separadas. La matriz siguiente se entregó antes de agregar tests; describe cobertura anterior y trabajo previsto, **no resultados PASS del nuevo PR**.

| Amenaza / frontera | Control y evidencia anterior | Brecha transversal |
|---|---|---|
| IDOR cross-tenant / HTTP→SQL | Membresía, HMAC/xid y FORCE RLS; TenantRlsTests/ResourceAuthorizationTests | Fixture A/B/C y efectos+journal+outbox juntos |
| IDOR same-tenant / cartera | Capacidad/asignación, resource_fence | Todos los perfiles en un harness y resultados trazables |
| UUID / enumeración | 404 uniforme | Respuesta segura y ninguna modificación/evento de éxito |
| Actor/rol/tenant falsificados | Sesión verificada, roles SQL, rechazo de headers/GUC | Combinar body/query/headers y atribución persistida |
| Firma/issuer/audience/expiración | Decoder contra JWKS real; state/nonce/PKCE HTTP | Tokens alterados por el callback HTTP completo |
| Cookie/fixation / sesión | Rotación y JDBC privado | Cookie manipulada y auth_version en sesiones A/B |
| CSRF / inicio OIDC | POST+CSRF y flag de un uso | Integración con efectos y journal |
| MFA / escalada | Perfil local revisado; sin jerarquía | Claims no autorizantes y mutación de política MFA |
| Revocaciones / TOCTOU | FOR SHARE hasta commit | Listado/lote/eventos con barreras y locks observables |
| Pool / threads / propagación | Contexto ligado a xid y callback | PID físico compartido, REQUIRES_NEW sin autoridad heredada |
| GUC / actor SQL | Registros privados y HMAC | Ataque combinado usando runtime real |
| RLS / privilegios / definer | Cercas, FORCE, guardas | Mutaciones restaurables, BYPASSRLS y funciones estrechas |
| Auditoría / manipulación | Sin lectura runtime, append-only | Oráculos enlazados al reporte y al resto del recorrido |
| Outbox / lease / consumidor | Scope por rol/OID, fencing/retry | Cross-tenant, autoridad revocada y efecto+ack |
| Lotes / rollback | Rollback de filas | También journal/outbox sin éxito falso |
| Cache de negocio | No implementada | Contrato de clave/invalidation; nunca PASS ficticio |
| Archivos / URLs / temporales | No storage documental implementado | Ausencia de rutas y contratos futuros trazables |
| Logs / trazas / artifacts | Proyección allowlist, canarios, sanitización | Reporte seguro y gate de casos críticos concretos |

La referencia TrailerSaaS aporta fixtures con membresías de estados distintos, consultas sin filtros, revocaciones frescas, barreras para claims y leases vencidos observables. No se copian su acceso tenant-wide, roles logísticos, Firebase, JPA ni autoridad de worker. Fiscuvia mantiene HMAC, asignación exacta y transporte sin autoridad de negocio.

## Estados y evidencia

La matriz ejecutable y el reporte publicable distinguirán IMPLEMENTADO Y VERIFICADO, IMPLEMENTADO CON VERIFICACIÓN PENDIENTE, CONTRATO DE SEGURIDAD PARA MÓDULO FUTURO y DECISIÓN PENDIENTE DE APROBACIÓN. Los casos futuros/pendientes no pueden producir PASS. El resultado de cada ejecución se vincula al SHA probado y al source SHA; no se incorporan filas, tokens, sesiones, URLs privadas o logs crudos.

## Matriz ejecutable y harness

La fuente revisable es [security-scenarios.mjs](../../tools/ci/security-scenarios.mjs): 28 escenarios críticos T30-01…28, cada uno con amenaza, frontera, control, ataque, expectativa, métodos JUnit exactos, evidencia, limitación y riesgo residual. Integra las suites #26–29 y 45 pruebas nuevas `CrossTenantRegressionTests`. `reports.mjs backend` exige tanto los mínimos por suite como los métodos concretos; una prueba no relacionada no sustituye un control crítico. Produce `.ci-artifacts/backend/regression.json`, sólo después de verificar cero fallos/omitidos. Los estados verificados describen **esa ejecución**, no un resultado anticipado de este documento. Una entrada ausente o rebajada a futura bloquea el gate.

`ThreatHarness` crea A/B/C por caso, diez perfiles exclusivos/multidespacho/administrador/supervisor/auxiliar/inactivos/sin membresía, dos carteras A y el mismo RFC DEMO en A/B. Las identidades se obtienen con login real Keycloak; no hay filtro falso. `ThreatFixtureController` y el esquema `threat_fixture` están sólo en test-classes, fuera de Flyway y del JAR. No se altera V1–V5 ni se aprueban capacidades fiscales: las políticas ACCOUNTANT/supervisor son sintéticas. El pool reutiliza sesiones válidas entre casos y reemplaza las invalidadas; tenants, asignaciones y comandos son nuevos en cada caso.

El proxy de ataque intercambia primero un código/PKCE real con Keycloak; después corrompe firma, issuer, audience o expiración del ID token y lo entrega al callback productivo que valida JWKS real. Los claims de rol firmados por el IdP tampoco conceden capacidades internas. No es una defensa contra un IdP productivo comprometido: éste sigue siendo raíz de confianza. Las claves efímeras sólo existen en memoria/Testcontainers.

Concurrencia: barreras para sesiones/replays/claims; latches para callbacks y consulta de `pg_locks` para demostrar que la revocación espera el bloqueo. El sondeo acotado espera una condición observable, no un retraso que se presume suficiente. Leases vencen mediante actualización administrativa del timestamp en la base desechable; no se relaja el protocolo. REQUIRES_NEW no hereda la autoridad exterior; una operación nueva debe autorizarse nuevamente. NESTED/savepoints continúan rechazados según el contrato #26. Threads virtuales y del pool no reciben autoridad por herencia.

## Contratos obligatorios para módulos futuros

**Cache:** no hay caché de negocio instalada. La clave deberá derivarse exclusivamente del backend e incluir versión de esquema, tenant autorizado, actor, auth_version, capacidad, tipo/ID de recurso, versión de política/asignación y filtros/página/orden normalizados. No almacenar tokens ni RFC en claves. Invalidar por logout/revocación/roles/políticas/asignaciones/estado del recurso; TTL no sustituye autorización. Probar colisión A/B y carteras A, poisoning de parámetros, revocación y resultados negativos antes de habilitarla. Requisito para búsqueda/listados y #54–55; no se acredita protección de Redis inexistente.

**Archivos/URLs/temporales:** autorizar sesión, membresía, capacidad y recurso antes de crear URL firmada o leer bytes; conocimiento del path no autoriza. TTL corto, ámbito de objeto/método, sin PII en URL/logs; política de revocación durante descarga explícita. Temporales con namespace tenant/operación aleatorio, permisos privados, sin directorio público y limpieza ante éxito/error/crash. Probar cross-tenant, misma cartera, traversal, expiración, revocación y limpieza en #46–48 y exportaciones. Las rutas inexistentes de archivos/exportación/jobs se comprueban denegadas; eso **no** acredita almacenamiento privado real ni todas las rutas que se creen después.

**Jobs:** el transporte existente sólo entrega su outbox autorizado. Un tenant_id del payload no concede autoridad de negocio, ni permite reusar sesión, token o AuthorizedAccess. Cada transacción posterior deberá revalidar permisos del actor o utilizar autoridad de servicio estrecha expresamente aprobada y auditable. ADR-008 sigue Proposed; no se introduce worker automático, usuario ficticio ni permiso fiscal. Los futuros #65–67 y productores de dominio deben añadir pruebas de revocación, versión obsoleta, idempotencia del efecto externo y frontera de servicio antes de habilitarse. La entrega sigue siendo at-least-once.

**Pendientes:** retención/capacidad/restauración operativa de infraestructura no se acreditan con estos tests. OQ-015, #74 y proveedor/MFA productivo requieren aprobación. Los siete escenarios F/P/D del reporte son NOT VERIFIED y jamás PASS. Para consulta de diagnóstico, usar el reporte sanitizado y los códigos seguros, no exportar tablas, sesiones, SQL, logs crudos ni trazas privadas.

## Ejecución y evidencia

Ver [guía de mutaciones](NEGATIVE-TESTING.md), [CI](../delivery/CI.md) y [evidencia #30](../../backend/VALIDATION-30.md). El reporte guarda `testedSha` (checkout ejecutado) y `sourceSha` (head de PR); GitHub puede probar un merge sintético. Cinco checks deben ser success sobre ese source SHA. No se modifican presupuesto $0, protecciones, billing ni permisos GitHub; #79 está cerrado tras #85. Una nueva restricción pre-runner se informa con evidencia, sin fingir CI verde.
