# Oleadas de implementación y dependencias Fiscuvia (propuesta)

**Gate vigente SPEC01 #74:** [revisión de base real y dependencias #34–45](../architecture/SPEC01-fiscal-domain-74.md). Antes de abrir implementación #34 se requiere aprobar D74-01..05 y diferir explícitamente las OQ fiscales no usadas por ese corte; no es autorización por pertenecer a Wave3. #38–45 mantienen sus bloqueos contables y no reciben migraciones por aprobar sólo contribuyentes. Base V1–V11 preservada.

**Status:** Proposed · **Sin fechas comprometidas**, dependencias explícitas. Se utiliza el patrón *Wave 0–n* de TrailerSaaS.

## Wave 0 — especificación aprobable
- Revisar y aprobar PR #1, FR/BR/NFR, ADR-001..015, estados, esquema y OpenAPI incompleto según alcance.
- #18 descubrimiento con contadores puede continuar; OQ fiscales bloqueantes se resuelven **antes de migraciones definitivas de obligación**.
- #19 conseguir/adjuntar referencia visual original y aprobar tokens. 
**Exit:** documentación revisada, decisiones 'Accepted' únicamente tras aprobación y source-of-truth conocido.

## Wave 1 — base ejecutable y UI shell
Paralelo: #20 scaffold Java/Spring Modulith, #21 React/Vite, #22 DB/Flyway, #52 design system/AppShell. Después: #23 CI, #24 OpenAPI/errores, #25 observabilidad, #56 visual regression.
**Exit:** ambos apps compilan/testean, PostgreSQL real, API dummy y shell marca Fiscuvia con screenshots reales. No implementar pantallas CRUD sin el kit #52.

## Wave 2 — seguridad e identidad (gate)
#26 DB RLS + tenant context -> #27 OIDC/membresías -> #28 capacidades/expediente -> #29 auditoría/outbox -> #30 aislamiento/regresión. Se puede cambiar orden técnico si se documenta dependencia equivalente; ningún cliente real antes de aislar datos.
**Exit:** dos despachos con mismo RFC ficticio aislados a nivel BD/API/export/archivo/job; sesión segura y permisos probados.

## Wave 3 — administración y contribuyentes
#31 aprovisionar despachos; #32 cuotas y #33 miembros en paralelo condicionado a identidad. Luego #34 API contribuyente -> #35 directorio visual -> #36 ficha -> #37 import CSV/Excel (P2 si necesario).
**Exit:** un owner puede crear equipo y contribuyente, verlo con filtro/paginación y no acceder a otro despacho.

## Wave 4 — obligaciones y documentos (dominio central)
Documentos #46->#47->#48 (puede iniciar tras #34/seguridad). Reglas/plantillas #38->#39->#40; representación visual #41, alertas #42. Tareas #43 y revisión #44 dependen de motor y políticas aprobadas; #45 filing y pago separados, con evidencia.
**Gate de esquema:** [#74](https://github.com/Dagt/Fiscuvia/issues/74) debe revisarse/aprobarse y OQ-001..006 deben resolverse antes de emitir Flyway de dominio fiscal que fije estados/pagos.
**Exit:** un mes de obligaciones generadas, revisadas, con acuse, pago separado, auditable, sin afirmación SAT falsa.

## Wave 5 — honorarios, panel y reportes
#49 cuotas -> #50 pagos/aging -> #51 UX cobranza; #53 dashboard, #54 búsqueda, #55 reportes después de módulos y roles. #56 QA visual continua de Wave 1, no se pospone hasta final.
**Exit:** MVP admin utilizable end-to-end con documentos, cartera, dashboard y exportaciones.

## Wave 6 — hardening y piloto
#70 backups/restore + #71 staging/monitoreo/rollback -> #72 UAT de despachos, privacidad/contratos y revisión seguridad. No hay puesta en producción automática.
**Exit:** owner autoriza piloto informado con datos de clientes tras G5/G6.

## Wave 7 — funcionalidades comerciales post-MVP
#57->#58->#59 portal contribuyente; #60->#61 billing; #62->#63->#64 CFDI import. Estas olas pueden priorizarse con validación comercial, pero no saltar security gates.

## Wave 8 — SAT y conciliación
#65 spike oficial SAT + decisión llaves aprobada -> #66 conector -> #67 durable download. Después #68 estados bancarios -> #69 conciliación human-in-loop. **No** empezar conector que custodia e.firma sin ADR/legal/consentimiento aprobado.

## Paralelismo seguro
Frontend de shell (#21/#52) puede avanzar junto a backend (#20/#22); dominio dependiente de permisos no. Integración SAT no es parte del MVP; 'más completo' no significa adelantarse a seguridad. Riesgos abiertos se registran en docs/product/open-questions.md y docs/delivery/RISKS.md.
