# Epic map and suggested implementation order
Each epic receives a GitHub tracking issue; implementation issues link to the epic and to acceptance gates.

| ID | Epic | Release | Depends on | Outcome |
|---|---|---|---|---|
| E00 | Governance, documents and visual system | Phase 0 | none | Approved product and design baseline |
| E01 | Repository, tooling, CI, modular backend | Phase 1 | E00 | Reproducible development |
| E02 | Tenant isolation, users, identity and audit | Phase 1 | E01 | Secure per-firm boundaries |
| E03 | SaaS tenant admin and plan entitlements | Phase 1 | E02 | Provision and control accounts |
| E04 | Taxpayer CRM and portfolio | Phase 2 | E02,E03 | Complete customer directories |
| E05 | Obligations, templates, periods and calendar | Phase 2 | E04 | Track fiscal work |
| E06 | Tasks, review workflow and evidence | Phase 2 | E05 | Accountable work completion |
| E07 | Private document management | Phase 2 | E04,E02 | Secure expediente |
| E08 | Firm fees and receivables | Phase 2 | E04 | Collection and aging |
| E09 | Dashboards, search, export, accessibility | Phase 2 | E05-E08 | Operational visibility |
| E10 | Client portal and document requests | Phase 3 | E07,E06 | Client collaboration |
| E11 | Subscription billing and operations | Phase 3 | E03 | Scalable commercialization |
| E12 | CFDI XML import and indexing | Phase 4 | E02,E07 | Structured invoices |
| E13 | SAT official download connector | Phase 4 | E12, key ADR | Authorized batch downloads |
| E14 | Assisted reconciliation and integrations | Phase 5 | E12,E13 | Cross-system workflows |
| E15 | Reliability, backup, restore and pilot gates | Cross-cutting | E01+ | Operational readiness |

## Roadmap completion policy
No epic marked done until its children pass Definition of Done and relevant security/UX acceptance. An epic is not an implementation task. Exact child issues may evolve with customer research.
