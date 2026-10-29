# Roadmap (dependency-led, not date promises)
This roadmap is **sequenced**. No delivery date or effort commitment until sizing, pilots and external integration validation.

## Phase 0 — Product foundations (documentation, prototype, research)
PRD, UX, ADRs, risk register, developer rules, issues and interviews. Deliverable: approved documents and acceptance gates.

## Phase 1 — Secure platform foundation
Repo scaffold, devcontainers/compose, Spring Boot modular monolith, React design system, GitHub Actions, PostgreSQL/Flyway/RLS, OIDC roles, audit, tenant provisioning, R2 adapters, telemetry. Deliverable: tenant-isolated vertical slice.

## Phase 2 — MVP operations
Taxpayer records; obligation templates/period recurrence; workflow and review; tasks; evidence/documents; expirations; fees; dashboard; report/export; onboarding and UAT. Deliverable: secure limited-pilot release.

## Phase 3 — Adoption and collaboration
Client portal, document requests/notifications, bulk import, subscription billing self-service, richer approvals/reporting. Deliverable: scalable commercial use.

## Phase 4 — CFDI capability
XML ingestion/indexing first, then official SAT connector following key-custody ADR, service-contract validation, async jobs, health and metering. Deliverable: audited authorized download capability.

## Phase 5 — Advanced automation
Assisted bank reconciliation, reconciliation approvals, third-party accounting export/import, profitability analytics, dedicated deployment option. No automated tax determination without separate specification and expert verification.

### Prioritization rule
Security, tenant correctness, data integrity and accepted UX precede feature breadth. An integration's regulatory feasibility must be resolved before schedule estimates.
