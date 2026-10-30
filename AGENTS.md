# AGENTS.md — Fiscuvia

**Producto:** Fiscuvia — Todo tu despacho, una sola vía. **Estado de esta rama:** base #20–25 integrada; tenancy/RLS #26 para revisión, sin esquema fiscal ni funcionalidad de negocio. CI remoto bloqueado con presupuesto $0; pruebas locales completas y seguimiento #79.

## Contrato para Codex (inspirado en el proceso real de TrailerSaaS)
Este documento establece reglas obligatorias para agentes/colaboradores. **Trabaja exclusivamente desde una issue hija acotada**, nunca desde una épica o el tracker #73 directamente. No confundir archivo propuesto con funcionalidad realizada.

### Lectura previa obligatoria
1. `README.md` y `docs/README.md`.
2. `docs/discovery/evidence-matrix.md` si se interpreta una necesidad real.
3. `docs/product/MVP.md` + `docs/product/requirements-catalog.md` + `docs/product/business-rules-catalog.md` + `docs/product/open-questions.md`.
4. `docs/product/actors-and-permissions.md` y `docs/security/permission-capabilities.md`.
5. ADR relevantes en `docs/decisions/` (estados Proposed no son autorizaciones).
6. Flujos `docs/workflows/` y máquinas `docs/state-machines/`.
7. `docs/data-model/schema-v0.1.md` y `docs/api/`.
8. `docs/ux/README.md`, `DESIGN-SYSTEM.md`, `screen-inventory.md`, `VISUAL-ACCEPTANCE.md` para toda UI.
9. `docs/backlog/implementation-order.md`, `docs/backlog/codex-workflow.md` y la issue exacta.
10. `docs/delivery/DEFINITION-OF-DONE.md` y pruebas afectadas.

### Orden de autoridad documental
Una vez explícitamente aprobados: **Accepted ADR > FR/BR/NFR aprobados > estados/workflows aprobados > MVP aprobado > descubrimiento verificado > hipótesis**. Propuestas no pueden considerarse aceptadas por estar en GitHub o en un PR abierto. Si hay conflicto, crear/actualizar OQ y pedir revisión; no decidir silenciosamente.

### Límites de producto
- Tenant = despacho; taxpayer = contribuyente de ese despacho. RFC es identificador comercial, no primary key ni frontera de autorización.
- Este producto NO sustituye CONTPAQi Contabilidad en MVP; enfocado a gestión de despacho, obligaciones, expedientes, honorarios y equipo. CFDI y SAT por fases; no implementar extracción SAT en MVP sin aprobación.
- Presentación de declaración, evidencia de aceptación oficial, pago fiscal y honorarios del despacho son conceptos independientes.
- Reglas fiscales/plazos deben provenir de expertos y documentación vigente, nunca una heurística inventada.
- Nada de astrología, música, noticias genéricas, contabilidad por pólizas o facturación PAC propia en MVP.

### Arquitectura y seguridad
- Java 25 LTS / Java 21 fallback previa prueba, Spring Boot compatible, Spring Modulith, React/TS/Vite, PostgreSQL/Flyway/RLS y object storage privado.
- Monolito modular y transacciones; separar identidad, clientes, obligaciones, revisión, documentos, honorarios, jobs, audit; no agregar K8s/microservicios/colas externas sin ADR.
- Usuarios verificados con OIDC, roles/capacidades/expedientes en backend, RLS con tenant context transaccional, tests de fuga entre dos despachos.
- Nunca almacenar contraseñas SAT, llaves e.firma .key/.cer, datos reales RFC/CFDI, backups, tokens ni secretos en Git, logs, frontend o screenshots.
- No implementar custodia de firma SAT hasta ADR-009, consentimiento/autorización, legal y threat model aprobados.
- API comando/evento explícito para estados, expectedVersion, idempotencia y auditoría; no PATCH libre de status.
- **Gate de diseño #74:** no generar migraciones definitivas del dominio fiscal antes de revisar OQ y esquema.

### Requisito visual igual de importante que funcionalidad
Conservar **identidad Fiscuvia navy/teal**, no replicar UI Fleet Simple de TrailerSaaS. Diseñar componentes y app shell antes que pantallas CRUD. Capturas **reales de la app ejecutándose**, al menos 1440×900 y 390×844 más estados vacíos/error, comparación con referencia aprobada y revisión de accesibilidad. Una imagen de IA NO es evidencia de UI implementada. No aceptar frontend genérico por ser funcional.

### Protocolo GitHub
1. Verificar prerequisitos, issue y latest main; rama `issue-N-descriptive-slug`.
2. Una issue hija por PR; implementación/test/docs/UX dentro de ese alcance.
3. Ejecutar checks locales y documentar comandos y SHA. Si CI no corre por billing, estado `REMOTE CI NOT VERIFIED`; nunca decir verde.
4. PR **Draft** con `Refs #N`, FR/BR/NFR/ADR, tests, migraciones, capturas reales, riesgos y faltantes.
5. **NO MERGE**, no desplegar, no cerrar issues manualmente ni marcar Ready sin indicación del owner.
6. No desactivar tests/RLS/auth ni añadir datos reales en fixtures. El owner decide cuándo mergear.

### Fuente del backlog
Tracker #73, épicas #2–17, hijos #18–72, gate documental #74. El backlog/requisitos son trazables en `docs/backlog/`. Los números indican planificación, no avance ya entregado.
