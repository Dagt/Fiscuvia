# Modelo de datos conceptual (no DDL definitivo)
- tenants(id,slug,brand,status,plan_id)
- users(id,identity_subject,status), memberships(tenant_id,user_id,role,status)
- taxpayers(id,tenant_id,rfc_normalized,name,person_type,regime,status,owner)
- taxpayer_contacts, taxpayer_assignments, tags
- obligation_templates(id,tenant_id,version,cadence,evidence_policy,review_policy)
- taxpayer_obligation_configs(tenant_id,taxpayer_id,template_id,effective_dates)
- obligation_occurrences(id,tenant_id,taxpayer_id,template_version,period_start,period_end,due_date,workflow_state,filing_state,payment_state,assignee)
- obligation_evidence(occurrence_id,document_id,type)
- tasks, task_events, comments
- documents(id,tenant_id,taxpayer_id,object_key,sha256,media_type,size,classification,uploaded_by)
- fee_agreements, fee_charges, fee_payments, fee_adjustments
- audit_events(tenant_id,actor_id,action,entity_type,entity_id,correlation_id,occurred_at,redacted_changes)
- jobs, outbox_events, subscriptions and entitlements
- CFDI later: cfdi_documents, cfdi_status_checks, sat_download_requests, sat_packages, reconciliation_candidates
## Invariantes
IDs UUID; tenant_id NOT NULL en datos de despacho, índices y claves compuestas por tenant para impedir referencias entre despachos. RFC puede repetirse entre tenants. Fechas de vencimiento DATE, timestamps UTC. Dinero BigDecimal/numeric y moneda explícita. Generación idempotente de obligaciones por cliente/plantilla versión/período. Nunca eliminación en cascada de auditoría o evidencia; retención y política legal pendientes. Migraciones Flyway versionadas, probadas sobre PostgreSQL real.
