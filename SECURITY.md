# Security policy (planning stage)
This repository does not yet ship a production product. Do not place sensitive data here. Report security issues privately to repository maintainers through GitHub private vulnerability reporting if configured; otherwise contact the project owner privately, not in a public issue.

**Absolute prohibitions:** never commit private .key files, e.firma passwords, SAT credentials, real RFC-based client fixtures, tax XML/PDF records, production tokens or backup dumps. Never implement credentials in localStorage, trust a client-provided tenant identifier, or log full XML/credentials.

Security release gates and architecture are specified in [security baseline](docs/security/SECURITY-BASELINE.md) and [SAT integration](docs/integrations/SAT-CFDI.md). No SAT key custody until threat model, legal authorization, KMS/HSM design and operational controls have been approved.
