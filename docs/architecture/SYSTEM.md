# Arquitectura de Fiscuvia (propuesta v0.1)
## Stack
Backend Java 25 LTS (Java 21 LTS si compatibilidad lo exige), Spring Boot estable compatible y Spring Modulith. Frontend React 19, TypeScript, Vite, Tailwind y shadcn/ui. PostgreSQL con Flyway y RLS; almacenamiento S3/R2 privado; identidad OIDC; OpenAPI; GitHub Actions; OpenTelemetry/Sentry.
## Arquitectura
Monolito modular: identity, tenants, taxpayers, obligations, workflow, tasks, documents, fees, reports, notifications, subscriptions, cfdi (posterior), satconnector (posterior). Cada módulo posee sus tablas, API públicas y eventos; evitar acceso directo a repositorios de módulos ajenos. Usar outbox transaccional para notificaciones y trabajos persistentes cuando sea necesario. No microservicios, Kafka ni Kubernetes en MVP.
## Flujo
React -> API con validación/autorización -> servicios de dominio -> PostgreSQL; archivos privados fuera de DB y registrados con metadatos. Jobs con contexto de tenant verificado. API y worker pueden usar la misma imagen, escalados separadamente según demanda.
## Operación
Entornos local/test/staging/prod separados. Secret manager; TLS; despliegue reproducible; health checks, métricas, trazas, logs redactados; backups y restauración probada. Contratos REST /api/v1, errores estándar y versionado. Recomendaciones sujetas a smoke test de dependencias al crear scaffold.

## Base efectiva #29 — infraestructura local/test

#20–28 están integrados en main `1f09a26fc0afbcc3498b825e1b3f676cda79d2f0`. Los módulos reales son platform, identity y audit; identity → audit → platform, sin ciclos. [Journal append-only](../security/AUDIT-JOURNAL.md) y [outbox/protocolo](../delivery/OUTBOX.md) añaden V5 sin cambiar V1–V4. Eventos reales de identidad y outbox se escriben en la misma transacción PostgreSQL; errores revierten el cambio sensible. Denegaciones verificadas usan una política global privada tras rollback, sin tenant ajeno.

No hay lector AUDIT_VIEW, worker automático, broker externo ni autoridad de negocio de sistema. El transporte, sin cuentas/scopes configurados al instalar, sólo puede manipular avisos de su cola explícitamente aprobada; no establece contexto de negocio ni serializa sesiones. Interfaces y consumidores sintéticos se prueban con roles efímeros en PostgreSQL real. Módulos fiscales, #74, proveedor productivo, matriz funcional y operación de workers siguen sujetos a aprobación. ADR-008 no se marca Accepted.

## Hardening transversal #30

Base actual #20–29 integrada desde f40a4980c1eaf268c16fcc39b2855dbcc71378a6 (#85). [Harness/matriz](../security/CROSS-TENANT-REGRESSION.md) combina identidad → autorización → contexto RLS → efecto/journal/outbox con oráculos negativos y gate por método/SHA. No cambia módulos ni puertos productivos, V1–V5 o dominio fiscal. Los adaptadores HTTP/SQL/emisor y claves adversariales son sólo test-classes y se inspecciona su ausencia en el JAR. Cache, archivos y autoridad de negocio de jobs tienen contratos futuros explícitos, sin simulación productiva.
