# Contratos de API Fiscuvia — propuesta v0.1

**Estado:** Proposed, no implementado. El contrato de la API se define antes de codificar endpoints; las rutas futuras no implican implementación.

1. [Convenciones REST, autorización, errores y concurrencia](conventions.md)
2. [Catálogo de endpoints por módulo](endpoint-catalog.md)
3. [Esqueleto OpenAPI de arranque](openapi-v1-draft.yaml) — intencionalmente parcial, NO especificación final de todas las rutas.
4. [Datos y constraints](../data-model/schema-v0.1.md)
5. [Máquinas de estados](../state-machines/README.md)

## Reglas
- `/api/v1/tenants/{tenantId}/...` expresa tenant **solicitado**, nunca prueba de autorización. Backend comprueba membresía activa, capacidad, expediente/alcance y estado. RLS detrás.
- `GET /api/v1/me` retorna identidad propia, no roles falsamente confiables desde JWT.
- Commands explícitos para las transiciones; PATCH de metadata no permite editar estados controlados.
- Actualizaciones mutables con `expectedVersion`, conflicto HTTP 409.
- Errores de entrada HTTP 400/422 según contrato, autenticación 401, permisos 403/404 según política; no revelar existencia cross-tenant.
- Jobs largos entregan 202 + estado consultable, no resultados ficticios.
- XML/PDF original fuera de JSON; object storage privado, flujo inicialización/finalización.
