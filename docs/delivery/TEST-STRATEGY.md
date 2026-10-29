# Estrategia de pruebas
Backend JUnit + Testcontainers PostgreSQL real: reglas dominio, estado fiscal, transacciones, Flyway, autorización, RLS y concurrencia. Contratos OpenAPI verificados. React con typecheck, lint, tests de componentes/interacción y E2E Playwright sobre app ejecutable.
## Escenarios obligatorios
- Dos tenants con mismo RFC sintético: sin acceso cruzado a API, DB, archivos, reportes, jobs ni cache.
- Obligaciones recurrentes idempotentes; cambios de estado con evidencia/rechazo, fechas límite revisadas.
- Pagos de honorarios distintos de pagos de impuestos; importes decimales y ajustes auditados.
- Archivos hostiles, XML con XXE, ZIP grande, MIME falso, accesos a URLs firmadas sin autorización.
- UI teclado, mobile, idiomas consistentes, loading/empty/error, capturas reales.
- Conector SAT futuro: reintentos, caducidad firma, fallas, límites y jobs reanudables.
## CI
Backend, web, OpenAPI, Flyway/RLS, secret scan, build Docker; evidencia al mismo SHA. Si CI GitHub no ejecuta por billing, reportar UNVERIFIED y adjuntar validación local equivalente sin simular éxito remoto.
## Datos de prueba
Ficticios, marcados DEMO; nunca datos personales, XML fiscales reales, certificados ni llaves de producción.
