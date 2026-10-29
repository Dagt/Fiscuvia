# Product requirements document (PRD) — v0.1
**Status:** proposed product baseline; approval required before implementation. **Audience:** accounting practices in Mexico. **Product:** subscription SaaS.

## Product boundaries
Two axes: `tenant` = accounting firm; `taxpayer` = firm's client. Taxpayer may be natural person or legal entity. Firm users access authorized taxpayer portfolios. Client-portal users get narrowly scoped self-service access (later).

## Roles (minimum)
- Platform operator: SaaS administration, **no default access to tenant fiscal records**.
- Firm owner/admin: firm users, policies, client portfolio, plan and billing.
- Supervisor: review tasks and approve according to configuration.
- Accountant: manage assigned customers/obligations and attach evidence.
- Assistant: prepare drafts, documents and assigned activities without approval rights.
- Taxpayer portal user (phase 2): only their designated account and shared documents.

## Functional domains
| Domain | MVP | After MVP |
|---|---|---|
| Tenant/account provisioning | manual onboarding, plan flags, isolation | self-service billing |
| Identity/permissions | login via OIDC, role + resource policy, MFA requirement for privileged users | richer policy/custom roles |
| Taxpayer directory | RFC validation format only, client data, regime, responsible, active status, tags | CSF importer with verified parsing |
| Obligations | tenant templates, recurrence, period, deadlines, review stages, evidence | regulatory template packs, rules versioning |
| Tasks | assign, due, activity feed, recurrent follow-ups | automations/escalation |
| Files | upload, safe storage, previews where supported, metadata, access controls | client portal exchange |
| Fees | periodic fees, charges, payment entry, balances, aging | billing integrations |
| Reports | summary, filters, CSV/Excel/PDF | deep operational analytics |
| Audit | immutability-oriented append-only event journal for sensitive actions | compliance exports |
| CFDI | XML import as optional early extension, not required for first pilot | direct SAT download, synchronization, status queries |
| Bank reconciliation | not in MVP | assisted matching, human approval |
| SAT declaration submission | **out of scope** | only after feasibility/legal assessment |

## Key user stories
US-001 Admin creates firm users and sets allowed roles.
US-002 Accountant onboards a taxpayer and assigns a responsible team member.
US-003 Accountant configures recurring obligations from a **reviewed** template.
US-004 System opens expected obligations for a fiscal period and marks late items.
US-005 Assistant submits evidence and requests supervisory review.
US-006 Supervisor approves or rejects with notes; audit trail preserved.
US-007 Accountant records filing date and related receipt; then payment evidence separately if relevant.
US-008 Firm records recurring fees, payments and overdue balances.
US-009 Staff obtains export filtered to their authorized taxpayers.
US-010 Operator can provision a tenant but cannot read fiscal documents absent controlled break-glass.

## Universal UX
Spanish Mexico, MXN and dates displayed with locale; RFC uppercase normalization; global search only within authorization. State changes explicit, no surprise data loss. Human-readable errors, empty/loading states, keyboard support, responsive navigation. See UX docs.

## Nonfunctional baseline
Security and privacy by design; tenant isolation and authorization tests; no sensitive data in logs; backups + demonstrated restore; observable jobs; tests, docs and operational procedures. User flows must be useful on 1440px desktop and 390px mobile (mobile tables use meaningful alternative).

## Explicit non-goals
No music, astronomy, horoscopes, news feed, Bible widgets. No accounting ledger, payroll, e-invoicing PAC replacement, automatic tax computation, tax representation or guaranteed SAT compliance in first release. No SAT credentials persisted in browser.

## Open decisions requiring validated discovery
- Per-plan limits (RFC, users, storage, job capacity) and currency/tax presentation.
- Exact obligations templates, evidence requirements and deadline rules by contributor; expert review.
- Scope of customer notifications and official SAT integration credentials.
- Pricing and billing provider; legal counsel's approval for privacy/terms.
