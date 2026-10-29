# Contributing to Fiscuvia
## Workflow
1. Verify issue scope and dependencies. Link PR with `Refs #N` (or `Closes #N` only when fully delivered after merge).
2. Branch from latest `main`: `issue-N-short-slug`.
3. Implement smallest vertical slice incl. DB migration/API/UI/test when relevant; avoid multiple unrelated concerns.
4. Self-review security, tenancy, UX, regressions and docs.
5. Open **Draft** PR with mandatory template. No auto-merge; human reviews and merges.
6. CI failure or billing restriction: attach local reproducible commands and logs, do not claim remote passes. Record block explicitly.

## Code standards
- English identifiers; Spanish Mexico UI; typed API contracts (OpenAPI), no implicit breaking changes.
- Domain validation server-side; client-side for usability only.
- No global tenant switching via user-supplied IDs; service/account permissions enforced server-side.
- Database changes Flyway additive/migration-safe; versioned migrations and rollback strategy; never edit applied migrations.
- Integration tests PostgreSQL/Testcontainers for RLS and transactions.
- Workflows with states and evidence, not arbitrary booleans.

## Pull request size
Target reviewable slices. Include visual screenshots for UI; before/after for redesign. Annotate any intentionally deferred functionality. Never commit secret files.
