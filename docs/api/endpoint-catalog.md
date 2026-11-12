# Catálogo de endpoints — estado explícito

**SPEC01 #74:** para la revisión de contribuyentes consultar [contrato completo #34 Proposed](taxpayers-34-specification.md), con archivo/restauración explícitos y perfil cerrado; las rutas de asignación/certificados siguen #36. No implementado. La [matriz fiscal](../security/FISCAL-PERMISSIONS-PROPOSAL-74.md) permanece pendiente; ninguna fila histórica habilita permisos. V11 sí implementa equipo según su guía/OpenAPI, prevaleciendo sobre notas históricas de este catálogo.

## Implementado en el corte local/test #33

GET `/api/v1/tenants/{tenantId}/team/invitation-options` y POST `/api/v1/tenants/{tenantId}/team/invitations`. Owner+MFA y MEMBERSHIP_INVITE existente, opt-in Keycloak local; destinatario verificado y issuer/subject exactos. Token sólo en primera respuesta; no envío externo ni lista de miembros/invitaciones. Rutas de rol/reactivación/cartera permanecen fuera de implementación. [Alcance aprobado](../security/LOCAL-EMAIL-INVITATIONS-33.md).

## Implementado en #31

GET y POST `/api/v1/tenants/{tenantId}/settings`, con sesión, capacidad, lifecycle y RLS; POST añade MFA, CSRF, Idempotency-Key y expectedVersion. La política permanente de edición continúa DENY hasta aprobar la propuesta mínima. `/me` y selección entregan nombres/estados de membresías propias. Provisioning/suspensión/reactivación se ejecutan por procedimiento administrativo privado, **sin rutas HTTP de operador**. [Autoridad, datos y límites](../architecture/TENANT-PROVISIONING.md), [OpenAPI implementado](openapi-v1.yaml). Las rutas de negocio restantes son propuestas.

## Implementado en #24 (para revisión)

| Método | Ruta | Alcance |
|---|---|---|
| GET | /api/v1/public/info | Información pública del producto; openapi-v1.yaml, sin datos de negocio ni identidad |

## Implementado en #27, sólo local/test

GET `/api/v1/me`, `/api/v1/auth/status`, `/api/v1/auth/csrf`; POST `/api/v1/auth/login`, `/logout`, `/tenant`, `/invitations`, `/invitations/accept`, `/invitations/revoke`, `/memberships/status` bajo el prefijo `/api/v1/auth` cuando corresponda. [OpenAPI implementado](openapi-v1.yaml) y [políticas/privilegios](../security/AUTHENTICATION.md) son la referencia exacta. Los redirects OAuth `/auth/authorize/local` y `/auth/callback/local` los gestiona Spring Security. No se publican CRUD de tenants/usuarios ni capacidades completas.

## Propuesto: no implementado

Prefijo T = `/api/v1/tenants/{tenantId}` con membership/permissions y RLS; rutas globales indicadas expresamente. Funciones posteriores identificadas Future.

## Identity / tenants
| Método | Ruta | Capacidad / notas |
|---|---|---|

| PATCH | T/settings | No implementado; #31 utiliza comando POST con capacidad, MFA, versión e idempotencia |
| GET | T/memberships | MEMBERSHIP_VIEW |
| POST | T/invitations | MEMBERSHIP_INVITE, token hash/expiry |
| POST | /api/v1/invitations/accept | invitación de un solo uso |
| POST | T/memberships/{id}/suspend | MEMBERSHIP_DISABLE |
| PUT | T/memberships/{id}/roles | MEMBERSHIP_ASSIGN_ROLE |
| GET | T/entitlements | TENANT_PLAN_VIEW |

## Contribuyentes
| Método | Ruta | Regla |
|---|---|---|
| POST | T/taxpayers | TAXPAYER_CREATE |
| GET | T/taxpayers | TAXPAYER_VIEW y cartera, paginado |
| GET | T/taxpayers/{id} | TAXPAYER_VIEW por recurso |
| PATCH | T/taxpayers/{id} | TAXPAYER_EDIT con expectedVersion |
| POST | T/taxpayers/{id}/archivals | TAXPAYER_ARCHIVE con motivo |
| PUT | T/taxpayers/{id}/assignments | TAXPAYER_ASSIGN |
| GET | T/taxpayers/{id}/certificate-expiries | CERTIFICATE_EXPIRY_READ |
| POST | T/taxpayers/{id}/certificate-expiries | metadata, sin llaves |

## Obligaciones y períodos
| Método | Ruta | Regla |
|---|---|---|
| GET/POST | T/obligation-templates | TEMPLATE_VIEW/MANAGE |
| POST | T/taxpayers/{id}/obligation-configs | OBLIGATION_CONFIGURE |
| POST | T/obligation-occurrences/generations | OBLIGATION_GENERATE, idempotencia, previsualización |
| GET | T/obligation-occurrences | OBLIGATION_VIEW, período/cliente/estado |
| GET | T/obligation-occurrences/{id} | Detalle y evidencia |
| POST | T/obligation-occurrences/{id}/work-starts | OBLIGATION_PREPARE |
| POST | T/obligation-occurrences/{id}/review-submissions | OBLIGATION_SUBMIT_REVIEW |
| POST | T/obligation-occurrences/{id}/review-decisions | OBLIGATION_APPROVE/REJECT |
| POST | T/obligation-occurrences/{id}/filings | OBLIGATION_RECORD_FILED, acuse |
| POST | T/obligation-occurrences/{id}/tax-payments | TAX_PAYMENT_RECORD |
| POST | T/obligation-occurrences/{id}/reopenings | OBLIGATION_REOPEN |
| POST | T/obligation-occurrences/{id}/not-applicable | OBLIGATION_MARK_NOT_APPLICABLE |

## Tareas/documentos
| Método | Ruta | Regla |
|---|---|---|
| GET/POST | T/tasks | TASK_VIEW/CREATE |
| GET/PATCH | T/tasks/{id} | TASK_VIEW/EDIT |
| POST | T/tasks/{id}/decisions | estado de tarea autorizado |
| POST | T/documents/uploads | DOCUMENT_UPLOAD inicializar |
| POST | T/documents/{id}/finalizations | validar upload |
| GET | T/documents | DOCUMENT_VIEW, paginado |
| GET | T/documents/{id} | DOCUMENT_VIEW |
| POST | T/documents/{id}/download-links | DOCUMENT_DOWNLOAD, corto |
| POST | T/documents/{id}/classifications | DOCUMENT_CLASSIFY |
| GET | T/audit-events | AUDIT_VIEW |

## Honorarios/reportes
| Método | Ruta | Regla |
|---|---|---|
| GET/POST | T/fee-agreements | FEE_AGREEMENT_VIEW/MANAGE |
| GET/POST | T/fee-charges | FEE_CHARGE_CREATE/VIEW |
| POST | T/fee-payments | FEE_PAYMENT_RECORD |
| POST | T/fee-adjustments | FEE_ADJUST |
| GET | T/fee-aging | FEE_REPORT_VIEW |
| GET | T/dashboard | autorización por KPI |
| GET | T/reports | REPORT_VIEW |
| POST | T/report-exports | REPORT_EXPORT, job asíncrono |
| GET | T/jobs/{id} | estado del trabajo propio |

## Future: CFDI/SAT/portal
`POST T/cfdi-imports`, `GET T/cfdi`, `GET T/cfdi/{id}`, `POST T/sat-download-requests`, `GET T/sat-download-requests/{id}` y portal /api/v1/portal/... . Todos **bloqueados** hasta FR/ADR/consentimiento aprobado.

## Contrato incompleto deliberadamente
Especificar request/response/error/status y authorization de cada ruta en un PR de diseño API antes de implementarla. Este catálogo no es OpenAPI listo para producción.

## Administración implementada A #33 (V11)

GET `/api/v1/tenants/{tenantId}/team/members`, GET `.../invitations` y POST `.../members/{memberId}/status`, `.../members/{memberId}/role`, `.../invitations/{invitationId}/revoke`. [Política y DTOs](../security/TEAM-ADMINISTRATION-33.md), [OpenAPI](openapi-v1.yaml). Sólo owner+MFA propio ACTIVE, sin endpoints de cartera o globales.
