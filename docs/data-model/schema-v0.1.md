# Esquema relacional PostgreSQL v0.1 — propuesta antes de migraciones

**Estado:** Proposed 2026-10-29 · **Base:** FR-###, BR-###, NFR-###, domain model, state machines, ADR-0002. Inspirado en el *nivel de precisión* del schema v1.0 de TrailerSaaS; **ninguna tabla está creada** y **no se considera aprobado** para Flyway hasta revisión de OQ fiscales bloqueantes.

## 1. Convenciones globales
- UUID PK; RFC normalizado como clave comercial, nunca PK. El mismo RFC puede existir en tenants distintos.
- Timestamps PostgreSQL `timestamptz` UTC; fecha fiscal de vencimiento `date`, zona IANA al calcular fecha si aplica.
- `numeric(18,2)` para MXN mostrado, contabilidad de impuestos/CFDI puede requerir precisión mayor definida por tipo de dato; no aproximar con double.
- Estado `varchar` + CHECK y versionado schema, nunca ordinal enum JPA.
- `version bigint NOT NULL DEFAULT 0` en agregados editables; `expectedVersion` para 409 conflicto.
- Todas las filas del dominio tenant-owned incluyen `tenant_id uuid NOT NULL` y `UNIQUE (tenant_id,id)` cuando referenciadas por FK compuestas.
- Foreign keys `(tenant_id,parent_id)->(tenant_id,id)`; ON DELETE RESTRICT en evidencia/histórico.
- Índices por `tenant_id` y filtros operativos, no índices globales que permitan exposición.
- `created_at`, `updated_at`, `created_by` cuando aplica; audit persistente e inmutable a nivel aplicativo.
- Datos de claves e.firma/SAT: **ninguna columna de contraseña/llave** en MVP.

## 2. Identidad y administración
### users (identidad global)
`id uuid PK, oidc_subject varchar UNIQUE NOT NULL, email varchar, display_name varchar, status varchar, created_at timestamptz`. No guardar contraseña del proveedor.
### tenants
`id uuid PK, slug varchar UNIQUE NOT NULL, name varchar NOT NULL, status varchar, branding_json jsonb, created_at timestamptz, version bigint`.
### tenant_memberships
`id uuid PK, tenant_id uuid FK, user_id uuid FK, status varchar, invited_at timestamptz, activated_at timestamptz, version bigint, UNIQUE(tenant_id,user_id)`.
### role_assignments
`id uuid PK, tenant_id uuid, membership_id uuid, role varchar, taxpayer_id uuid NULL, status varchar`. FKs tenant-safe. Roles y scopes no se aceptan del cliente como claims autónomos.
### invitations
`id uuid, tenant_id uuid, email varchar, token_hash varchar, expiry timestamptz, accepted_at timestamptz NULL, revoked_at timestamptz NULL, role_spec jsonb, created_by uuid`. Token bruto sólo se muestra una vez.

## 3. Contribuyentes
### taxpayers
`id uuid PK, tenant_id uuid NOT NULL, rfc_normalized varchar NOT NULL, legal_name varchar NOT NULL, person_type varchar, status varchar, primary_owner_membership_id uuid NULL, version bigint, created_at, updated_at`. UNIQUE parcial tenant+RFC donde status activo (resolver políticas de duplicado). Índice compuesto `(tenant_id,status,legal_name)`.
### taxpayer_regime_history
`id uuid, tenant_id uuid, taxpayer_id uuid, regime_code varchar, valid_from date, valid_to date NULL, source varchar, recorded_by uuid`. Sin solapamientos no revisados; régimen es referencia no comprobación SAT.
### taxpayer_contacts
`id uuid, tenant_id uuid, taxpayer_id uuid, name, email, phone, contact_role, version`.
### taxpayer_assignments
`id uuid, tenant_id uuid, taxpayer_id uuid, membership_id uuid, assignment_type varchar, active_from date, active_to date NULL`; permitir múltiples responsables sólo con decisión OQ-013.
### certificate_expiries
`id uuid, tenant_id uuid, taxpayer_id uuid, cert_type varchar (EFIRMA/CSD/OTHER), expires_on date, source varchar, recorded_at timestamptz, status varchar`. **Sin .key, PIN, contraseñas ni tokens.**

## 4. Obligaciones, períodos y revisión
### obligation_templates
`id uuid, tenant_id uuid, code varchar, version int, name varchar, cadence varchar, due_date_policy jsonb, evidence_policy jsonb, review_policy jsonb, status varchar, created_at`. UNIQUE tenant+code+version. JSON políticas requieren validación schema y aprobación del despacho; nunca cálculo legal asumido.
### taxpayer_obligation_configs
`id uuid, tenant_id uuid, taxpayer_id uuid, template_id uuid, effective_from date, effective_to date NULL, owner_membership_id uuid, status varchar`.
### obligation_occurrences
`id uuid, tenant_id uuid, taxpayer_id uuid, template_id uuid, template_version int, period_start date, period_end date, due_on date, work_state varchar, filing_state varchar, tax_payment_state varchar, version bigint, created_at`.
UNIQUE tenant+taxpayer+template_id+template_version+period_start+period_end; vigencia temporal y excepciones de calendario revisadas. NOT NULL y CHECK de estados propuestos. Índice `(tenant_id,due_on,work_state)`.
### obligation_reviews
`id uuid, tenant_id uuid, occurrence_id uuid, attempt int, state varchar, submitted_by, decided_by NULL, reason text NULL, created_at, decided_at NULL`. UNIQUE tenant+occurrence+attempt.
### filing_records
`id uuid, tenant_id uuid, occurrence_id uuid, filing_kind varchar, submitted_on date, recorded_by uuid, receipt_document_id uuid NULL, source varchar, verification_status varchar, verified_at timestamptz NULL, reference varchar, created_at`. Permite complementarias como nuevos registros.
### tax_payment_records
`id uuid, tenant_id uuid, occurrence_id uuid, filing_record_id uuid NULL, paid_on date, amount numeric, evidence_document_id uuid NULL, source varchar, recorded_by uuid, created_at, reversal_of_id uuid NULL`. Diferentes de pagos de honorarios.

## 5. Tareas y documentos
### tasks
`id uuid, tenant_id uuid, taxpayer_id uuid NULL, occurrence_id uuid NULL, title varchar, description text, state varchar, priority varchar, due_on date, assigned_membership_id uuid NULL, version bigint`.
### task_events
`id uuid, tenant_id uuid, task_id uuid, event_type varchar, actor_id uuid, reason text NULL, occurred_at timestamptz`.
### documents
`id uuid, tenant_id uuid, taxpayer_id uuid NULL, object_key varchar UNIQUE, sha256 varchar, original_filename varchar, media_type varchar, byte_size bigint, classification varchar, state varchar, uploaded_by uuid, uploaded_at timestamptz, version bigint`.
### document_links
`id uuid, tenant_id uuid, document_id uuid, target_type varchar, target_id uuid`. Validar target concreto por servicio para prevenir vínculo cruzado; target_type genérico no puede sustituir FKs/constraints en diseño final. Enlaces PDF/XML temporales sólo por autorización.

## 6. Honorarios y auditoría
### fee_agreements
`id uuid, tenant_id uuid, taxpayer_id uuid, monthly_amount numeric, currency varchar, cadence varchar, starts_on date, ends_on date NULL, version bigint`.
### fee_charges
`id uuid, tenant_id uuid, taxpayer_id uuid, agreement_id uuid NULL, period_start date, due_on date, amount numeric, currency varchar, state varchar, version bigint`.
### fee_payments
`id uuid, tenant_id uuid, paid_on date, amount numeric, currency varchar, method varchar, external_ref varchar, recorded_by uuid, created_at, reversal_of uuid NULL`.
### fee_allocations
`id uuid, tenant_id uuid, payment_id uuid, charge_id uuid, amount numeric`, CHECK amount>0; sum allocations bounded by payment amount through transaction policy.
### fee_adjustments
`id uuid, tenant_id uuid, charge_id uuid, amount numeric, reason, authorized_by uuid, created_at`.
### audit_events
`id uuid, tenant_id uuid NULL (platform operations), actor_user_id uuid NULL, action varchar, entity_type varchar, entity_id uuid, source varchar, correlation_id varchar, occurred_at timestamptz, changes_redacted jsonb`.
### outbox_jobs
`id uuid, tenant_id uuid NULL, type varchar, idempotency_key varchar, payload_redacted jsonb, state varchar, attempts int, not_before timestamptz, leased_until timestamptz NULL, created_at, completed_at NULL`. Aislamiento tenant worker revisado, backoff y recuperación.

## 7. Extensión posterior (NO crear en MVP)
`cfdi_documents`, `cfdi_status_checks`, `sat_download_requests`, `sat_download_packages`, `bank_movements`, `reconciliation_candidates`; no campos de key privada sin ADR aprobado.

## 8. Aislamiento PostgreSQL
Aplicar RLS explícito `USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)` con tratamiento fail-closed del valor ausente (no asumir que casteo NULL valida identidad); `WITH CHECK` equivalente; rol DB runtime sin BYPASSRLS, sin ownership privilege y con `FORCE ROW LEVEL SECURITY` cuando corresponda. Contexto `SET LOCAL` en transacción segura. Cuidado: jobs y entidades globales requieren políticas específicas. Probar fugas con pool/reuso y RLS en BD real.

## 9. Gates antes de la primera migración de dominio
Revisión de OQ-001–006/011/013/014/015 y políticas de roles. Aceptar formalmente modelo/ADR en PR, completar FKs y referencias, denormalización, uniqueness de contactos, escala de dinero/catálogos, datos de identidad externa, índices, delete/retención. **No derivar automáticamente Flyway de este documento sin revisión.**
