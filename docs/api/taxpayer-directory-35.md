# Contrato efectivo de lectura/presentación C02 #35

Extensión aditiva del [contrato #34](taxpayers-34-specification.md) y [OpenAPI implementado](openapi-v1.yaml). No cambia escrituras fiscales, grants, motivos o matriz D74-03. Tipos generados reproducibles en `web/src/shared/api/schema.d.ts`; adaptador `taxpayer-client.ts`, único responsable de validar respuestas fiscales para React.

| Método y ruta relativa a /api/v1 | Resultado |
|---|---|
| GET /tenants/{tenantId}/taxpayers?search=&status=ACTIVE&regime=000&page=0&size=20&sort=legalName,asc | TaxpayerPage existente, sin total; regime nuevo opcional |
| GET /tenants/{tenantId}/taxpayers/presentation | Señales de alta de la colección autorizada |
| GET /tenants/{tenantId}/taxpayers/{taxpayerId}/presentation | Señales de acciones del expediente exacto y su versión |

TaxpayerPresentation es cerrado: canCreate, createBlock (`NONE|NOT_AUTHORIZED|MFA_REQUIRED|QUOTA_UNAVAILABLE`), canEdit, canArchive, canRestore y version (NULL colección, entero seguro recurso). No contiene identidades, cartera, responsables, cuotas numéricas, reglas ni capabilities arbitrarias. Los GET usan sesión real, autorización TAXPAYER_VIEW, RLS y no-store. Error 401 sesión, 403 colección sin permiso, 404 recurso inexistente/ajeno/no autorizado, 400 UUID inválido y 500 fallo interno seguro. La señal no es garantía de ejecución ni reserva de cuota.

`regime` vacío o tres dígitos identifica una referencia **registrada en el snapshot vigente** del contribuyente; no interpreta si está fiscalmente vigente hoy, no confirma SAT ni infiere obligaciones. EXISTS correlacionado por tenant, UUID y versión se ejecuta junto con filtros/aislamiento antes de ORDER/LIMIT/OFFSET. Usa el índice primario existente `(tenant_id,taxpayer_id,taxpayer_version,ordinal)` para como máximo diez referencias del snapshot por candidato. No se añaden catálogos/opciones/conteos que permitan descubrir valores de cartera ajena. Mantiene coste acotado por página y la búsqueda paginada existente; no se promete índice universal para ILIKE. Una cartera muy grande requerirá medición/índice de búsqueda en una historia específica, sin descargarla a React.

V13 añade exclusivamente las funciones de presentación y su helper privado. Ninguna tabla fiscal o migración V1–V12 se modifica. La función SECURITY DEFINER exige contexto VIEW exacto y tenant/actor/membresía fijados por backend; el helper privado carece de EXECUTE runtime/sessions/PUBLIC. El cálculo de capacidad se limita a propietario independientemente elegible para CREATE con MFA y no devuelve registros/conteos; limpia el scope de medida antes de volver a SQL runtime. El guard de arranque comprueba propietario, search_path y privilegios de la función pública. No instala grants ni altera políticas de capacidad.

React usa AbortSignal, cache por actor/tenant/membresía, search en memoria, PATCH con CSRF y UUID idempotente, comandos con expectedVersion y motivos cerrados. Obtiene detalle y proyección con versiones coincidentes; si cambian entre ambos GET, muestra error recuperable y no mezcla las señales. [Flujos y estados](../ux/TAXPAYER-DIRECTORY-35.md).

Filtro/columna responsable pendientes de #36: no se publica la relación PRIMARY ni se amplía acceso a colaboradores mediante TAXPAYER_VIEW. No hay endpoint genérico de permisos.
