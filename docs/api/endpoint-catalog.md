# Catálogo de endpoints (propuesta, no implementados)

Prefijo T = `/api/v1/tenants/{tenantId}` con membership/permissions y RLS; rutas globales indicadas expresamente. Funciones posteriores identificadas Future.

## Identity / tenants
| Método | Ruta | Capacidad / notas |
|---|---|---|
| GET | /api/v1/me | identidad/tenants del usuario autenticado |
| GET | T/settings | TENANT_READ |
| PATCH | T/settings | TENANT_SETTINGS_MANAGE y expectedVersion |
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
