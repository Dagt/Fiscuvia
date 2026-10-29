# Arquitectura de Fiscuvia (propuesta v0.1)
## Stack
Backend Java 25 LTS (Java 21 LTS si compatibilidad lo exige), Spring Boot estable compatible y Spring Modulith. Frontend React 19, TypeScript, Vite, Tailwind y shadcn/ui. PostgreSQL con Flyway y RLS; almacenamiento S3/R2 privado; identidad OIDC; OpenAPI; GitHub Actions; OpenTelemetry/Sentry.
## Arquitectura
Monolito modular: identity, tenants, taxpayers, obligations, workflow, tasks, documents, fees, reports, notifications, subscriptions, cfdi (posterior), satconnector (posterior). Cada módulo posee sus tablas, API públicas y eventos; evitar acceso directo a repositorios de módulos ajenos. Usar outbox transaccional para notificaciones y trabajos persistentes cuando sea necesario. No microservicios, Kafka ni Kubernetes en MVP.
## Flujo
React -> API con validación/autorización -> servicios de dominio -> PostgreSQL; archivos privados fuera de DB y registrados con metadatos. Jobs con contexto de tenant verificado. API y worker pueden usar la misma imagen, escalados separadamente según demanda.
## Operación
Entornos local/test/staging/prod separados. Secret manager; TLS; despliegue reproducible; health checks, métricas, trazas, logs redactados; backups y restauración probada. Contratos REST /api/v1, errores estándar y versionado. Recomendaciones sujetas a smoke test de dependencias al crear scaffold.
