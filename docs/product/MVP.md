# MVP specification and gates
## MVP definition
First paid pilot-worthy **operations hub** for a limited number of accounting firms, once security, usability and operational gates are met. Feature flags control unfinished modules.

### Included (MUST)
- Secure onboarding of firms + role-based users.
- Tenant-scoped taxpayer directory, tags, assignments and regimes as configurable reference data.
- Configurable obligation catalog/templates; recurring per-period obligations and calendar/list views.
- State workflow `pending -> in_progress -> in_review -> filed` (or `not_applicable`) and separately `payment_pending -> paid` for obligations with payment requirement. Rejections return to in_progress. Every transition audited; evidence configurable.
- Task management with assignee, due date, comments and statuses.
- Document uploads with per-file authorization, safe storage and download.
- Fee schedule, charges, manual payment recording and aging report; do not conflate with taxpayer SAT payments.
- Dashboard, filters, exports, upcoming expirations of e.firma/CSD metadata (no secrets).
- Organization settings, audit trail, backups/restoration, error monitoring.
- Quality gates: accessibility, privacy, tenant isolation, performance smoke and responsive UX.

### Explicitly NOT included in MVP
- Direct SAT CFDI downloads or credential custody.
- Automatic declarations, taxes calculation, accounting ledger.
- Bank connections, automatic reconciliation, fiscal news/music/astrology.
- Full taxpayer-facing portal, WhatsApp messaging, autonomous AI tax advice.
- Automated card billing (manual pilot activation acceptable, with finance controls).

## Release gates
G0 Documentation approved and mockup direction accepted.
G1 Secure foundation (identity, RLS, audit, migrations, tests) demo.
G2 Operations vertical slice works with seeded synthetic taxpayer.
G3 Fiscal period obligations and evidence workflow demo with transitions.
G4 Fees, documents, dashboard, visual QA and exported reports.
G5 Pen test/independent security review appropriate to risk, restore drill, privacy/terms approved, monitored staging, accountant UAT.
G6 Limited pilot with written DPA/consent and validated support process.
**Never** deploy real taxpayer secrets based solely on passing unit tests.

## Pilot definition of success
At least 2 real firm workflows validated end-to-end, one document cycle with explicit permissions, no unresolved critical security issues, no known cross-tenant leaks, correct restore demonstration. Performance targets formalized with realistic synthetic load before pilot.
