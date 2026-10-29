# Business rules — provisional, subject to accountant validation
## Terminology and invariants
- Tenant = contracted firm. Taxpayer = contributor served by that firm. Same RFC could be represented in different tenants; these records must remain isolated.
- Mexican RFC syntax may be validated, but **regex alone does not verify official identity or tax status**.
- Obligation templates are **editable, versioned reference configurations**, never presumed comprehensive for any regime.
- Tax deadlines require authored calendars and expert review; don't derive them from an unverified simple formula.
- Periods must use locale-aware fiscal dates, no automatic timezone changes to deadlines; store timestamps UTC, represent calendar deadlines as LocalDate with explicit jurisdiction/timezone.
- Workflow: documented user action, named actor and event time. Filing and payment are distinct; evidence should be mandatory per configured workflow. An 'approved' status is not evidence of acceptance by the SAT.
- Only authorized roles can reopen completed periods, and reason + audit event required.
- Recurrence idempotency key = tenant + taxpayer + templateVersion + period; prevent duplicate creation and manually reconcile template changes.
- A cancelled/archived taxpayer must not silently erase audit trail or historical periods.
- Fees billed by accounting firm and taxpayer's tax liability are **different ledgers**. Every payment edit generates audit record; negative adjustments via explicit adjustment transactions, no destructive edits.
- Documents: immutable content identity, metadata revisions, private downloads via scoped expiring access. PDFs generated from user data require proper encoding/sanitization.
- Lists and reports enforce object authorization and tenant scope on every row and export.
- Never assume uploading a tax receipt proves validity; review/verification and provenance captured.
- Amounts as decimal fixed precision, ISO currency MXN, explicit tax/rounding rules; avoid binary floating point.
- Soft delete where appropriate; no blind hard delete of evidence, audit logs or transaction records.
