# Personas and journeys
## Personas
1. **Firm owner (1–15 staff)**: wants visibility over customers, unpaid fees, late obligations and team capacity. Limited time; trusts clear summaries.
2. **Senior accountant / supervisor**: coordinates filings and evidence; needs review queue, clear exceptions, accountability.
3. **Junior accountant / assistant**: follows repeatable checklists and due dates, attaches supporting files and requests approvals.
4. **Administrative collector**: tracks firm invoices, payments and delinquent fees; should not receive unnecessary tax secrets.
5. **Taxpayer/client user** (phase 2): low-complexity upload and download portal, clear requests and notifications.
6. **Platform operations team**: infrastructure and billing; no default access to customer tax documents.

## End-to-end journeys
A. **Firm onboarding**: admin joins -> configures branding, users, roles -> imports synthetic or authorized clients -> creates reviewed obligation templates -> assigns ownership.
B. **Monthly fiscal cycle**: obligation generated -> user sees it in calendar/list/matrix -> prepares work -> attaches receipt or evidence -> review -> filed -> payment verified separately -> audit/report.
C. **Expiring certificate**: admin records certificate validity metadata (not secret) -> reminder -> replacement date updated with audit.
D. **Firm fee collection**: recurring charge generated -> payment registered -> aging balance and receipt optional -> accountant sees firm balance (not SAT tax).
E. **CFDI import (later)**: accountant imports XML -> classifier extracts validated fields -> duplicate UUID handling -> reports and optional reconciliation assistance.
F. **Portal (later)**: accountant requests documents -> client uploads -> staff reviews -> client can see approved deliverables only.

## Research checklist
Interview 5–10 accountants; observe actual monthly close. Compare legacy HTML and CONTPAQi Optimiza **without asserting feature gaps**. Measure time in each tool, exceptions, documents, existing portals, pricing tolerance. Capture consent and do not collect production credentials or documents.
