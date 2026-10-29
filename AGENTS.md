# Instructions for Codex — Fiscuvia

## Mission and authority
Build **Fiscuvia — Todo tu despacho, una sola vía**, a trustworthy, elegant SaaS for Mexican accounting practices. This document is binding for repository contributors and agents. Never infer implementation completion from a mockup, issue title, or unmerged PR. Source of truth: `docs/product/PRD.md`, `docs/product/MVP.md`, architecture ADRs and the explicitly assigned GitHub issue. Conflicts or ambiguity: document and request review rather than invent fiscal rules.

## Operating protocol (MANDATORY)
1. Read relevant docs and current repository before edits; cite assigned issue and scope.
2. One focused issue/vertical slice per branch, named `issue-<number>-<slug>`; no unrelated refactoring.
3. Create a **Draft PR**; **do not merge**, close issues as completed, delete branches, change production infrastructure, deploy, or commit credentials without explicit user approval.
4. Present changed files, architecture implications, data migrations, screenshots, tests run, known limitations and next steps. Don't claim CI is green if remote CI unavailable; run and report local verification.
5. Do not silently relax tests, suppress failures, disable RLS/auth, bypass input validation, or substitute in-memory tests for required PostgreSQL/Testcontainers tests.
6. Make minimal, reviewable commits; no fake integrations, mocked production credentials or unrequested scaffolding masquerading as shipped functionality.

## Boundaries
- Backend modular monolith; modules own their models, services and migrations, communicate via published interfaces/events.
- Secure tenant isolation at every entry point including background jobs, exports, search, file objects and logs.
- RFC/customer belongs to a tenant; never equate a taxpayer with a tenant.
- Respect domain states, evidence and approval rules; a checkbox does not prove SAT submission/payment.
- No plaintext SAT secrets; integration and key custody require ADR and threat model first.
- Never fetch legal/administrative data via unauthorized browser automation. Prefer official/documented integrations.
- No real PII or customer XML in tests, screenshots, logs or fixtures.
- Avoid adding heavy infra (Kafka/K8s/microservices) before measurable need.

## UX is a release requirement
Read `docs/ux/DESIGN-SYSTEM.md`, `SCREENS.md`, `VISUAL-ACCEPTANCE.md` before any UI task. Build Fiscuvia branded components, responsive layouts, realistic Mexican fiscal workflows and intentionally designed empty/loading/error states. No generic bare CRUD screens or default scaffold. Add real browser screenshots (desktop 1440px and mobile 390px where applicable) to each frontend PR; compare explicitly against target specifications. Record deviations and obtain human review. A generated image is NOT screenshot evidence.

## Definition of done
Read `docs/delivery/DEFINITION-OF-DONE.md`; every feature includes access control, audit, tenant tests, validation, accessibility, docs, error handling, seed data, observability and relevant E2E evidence. API contract changes require synchronization with frontend and tests.

## Document-first first PR
The first implementation PR must create runnable skeleton and architecture tests **only after** this documentation PR is approved and merged.
