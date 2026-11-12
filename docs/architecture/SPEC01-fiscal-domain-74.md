# SPEC01 — modelo fiscal y preparación de #34

**Estado: Proposed, pendiente de revisión del propietario y especialista; 2026-11-12.** Refs #74. Este documento prepara decisiones; no las aprueba, no completa #34 ni habilita migraciones. Ninguna OQ se marca Resolved. Presupuesto $0, sin merge ni despliegue.

## Lectura y resultado de la revisión

Base actualizada mediante fetch, switch main y pull --ff-only: `05dc33e77e290777c316d26edabb7ce1e7b28c44`, padre `19d6ec043ccbfda3c75c39b3e5428a72666cd3ba`. Último merge: [PR #91, equipo #33](https://github.com/Dagt/Fiscuvia/pull/91), commit del 2026-11-11. Rama documental: `issue-74-fiscal-domain-specification`.

Se revisaron AGENTS/README/mapa documental, discovery/evidence-matrix, PRD/MVP/FR/BR/OQ, actores/capacidades, ADR-001/003/005/006/007/008/009/010/011/014/015, workflows/estados, esquema/API, UX, backlog/DoD/CI, código identity/tenants/entitlements/audit y Flyway V1–V11. Se consultaron íntegramente las issues [#74](https://github.com/Dagt/Fiscuvia/issues/74), [#18](https://github.com/Dagt/Fiscuvia/issues/18) y #34–45. #74 tiene cero comentarios en esta revisión. #18 permanece abierta: no hay validación profesional documentada que permita aprobar reglas fiscales.

Entregables para revisar juntos:

| Documento | Responsabilidad |
|---|---|
| [Esquema v0.1.1](../data-model/schema-v0.1.1-proposed.md) | Enmienda relacional, restricciones, RLS y evolución por módulo |
| [Dominio revisado](../domain/fiscal-domain-v0.1.1.md) | Agregados, estados, obligaciones, documentos y honorarios |
| [Permisos](../security/FISCAL-PERMISSIONS-PROPOSAL-74.md) | Matriz propuesta y frontera con V11 |
| [Decisiones/OQ](../product/SPEC01-decisions-74.md) | Alternativas, consecuencias y aprobaciones concretas |
| [Especificación #34](../api/taxpayers-34-specification.md) | Contratos, transacciones, errores, pruebas y frontera UI |
| [OpenAPI #34 Proposed](../api/openapi-taxpayers-34-proposed.yaml) | Contrato aislado y verificable; no genera cliente productivo |
| [Validación](../delivery/VALIDATION-74.md) | Comandos, evidencia y límites de esta entrega documental |

## Base efectiva que se conserva

| Migración | Resultado efectivo y frontera |
|---|---|
| V1 | Namespace app; infraestructura, sin fiscal |
| V2 | app.tenants(tenant_id), contexto privado xid8, FORCE RLS; una GUC sola no es autoridad |
| V3 | identity_private.users/external_identities, identity.memberships/membership_roles/invitations, sesiones privadas; issuer+subject, no email como identidad |
| V4 | role_capabilities, resource_grants, authorize_operation/resource_allowed, conjunto de UUID congelado por transacción; sin contribuyentes reales |
| V5 | journal.events/outbox, commands, denials, transporte acotado; sin worker ni lector público |
| V6 | tenant_private.profiles, lifecycle y comandos administrativos; raíz antes de membresía/política |
| V7 | Política aprobada exclusivamente TENANT_SETTINGS_MANAGE owner+MFA |
| V8 | Límites/reservas privadas; sólo USERS integrado; TAXPAYERS todavía NOT_INTEGRATED |
| V9 | Política aprobada TENANT_PLAN_VIEW owner+MFA y trigger real de cupos de membresías |
| V10 | Contacto verificado e invitación exacta por correo local/test, hash de token de un uso y entrega manual |
| V11 | Equipo owner+MFA, rol operativo/estado/versiones/idempotencia; no cartera ni permisos fiscales |

Identidad global no se recrea. Las futuras FK de miembros apuntan a `identity.memberships(tenant_id,id)` y la raíz a `app.tenants(tenant_id)`, nunca a las tablas hipotéticas users/tenants del esquema v0.1. `AuthorizedTenantOperations` permanece único puerto de negocio; no depender de TenantTransactions desde taxpayers. JDBC/SQL explícito ya existe: no introducir JPA/jOOQ sólo por el texto Proposed de ADR-003. Java25/Boot4.1.2/Modulith2.1.2 y arquitectura modular actuales se conservan sin cambios de dependencias.

Las aprobaciones parciales de #27/#31/#32/#33 y referencia visual OQ-021 se conservan exactamente como están enlazadas en sus guías. Ningún ADR completo cambia de Proposed a Accepted. Las políticas fiscales efectivas siguen **DENY**, incluso para owner. AUDIT_VIEW tampoco se habilita.

## Hallazgos y tratamiento de contradicciones

| Hallazgo en v0.1 / documento histórico | Propuesta y decisión pendiente |
|---|---|
| Identidad propuesta no coincide con V3; tenant propuesto usa id en lugar de tenant_id | Usar base física V1–V11, no migrar de nuevo identidad/tenant |
| UNIQUE parcial RFC activo permite dos expedientes históricos del mismo RFC | D74-01 propone UNIQUE tenant+RFC para toda la vida del expediente; restaurar el mismo UUID |
| FR-015/borrador API incluyen INACTIVE; #34 pide ACTIVE/ARCHIVED | D74-01 propone dos estados para #34; INACTIVE diferido, sin interpretar baja fiscal SAT |
| Régimen singular y prohibición genérica de solapamiento | D74-02 propone conjunto de referencias con vigencia, admite regímenes distintos simultáneos; no catálogo fiscal inventado |
| Responsable duplicado en taxpayers y assignments | Una relación temporal; no columna primary_owner que pueda divergir |
| resource_grants referencia UUID genérico sin FK al recurso real | Columna generada nullable por tipo + FK compuesta futura; no registro paralelo de autoridad |
| Alta exige asignación a recurso que todavía no existe | D74-03: TAXPAYER_CREATE existente, target TENANT, función estrecha; grants iniciales exactos en el mismo commit, lectura posterior reautorizada |
| Capacity acepta sólo MEMBERSHIP_INVITE y reservations.reference_id apunta a users | #34 necesita productor TAXPAYERS propio, no pasar taxpayerId por reserva USERS ni fingir medición |
| Unicidad de ocurrencia incluye versión de plantilla: cambio de versión puede duplicar período | OQ-001/004: identidad estable de obligación configurada + período; versión es procedencia congelada, no identidad de recurrencia |
| document_links polimórfico carece de FK concreta y relación mismo contribuyente | Enlaces tipados con FK tenant+taxpayer+target; sin destino UUID libre |
| Borrador API usa Bearer y allOf cerrado incompatible; usa total supuesto | Contrato #34 aislado de sesión BFF, schemas cerrados sin allOf, PageResponse con hasNext |
| Tablas fiscales v0.1 sugieren audit JSON/outbox genérico | Reutilizar journal/outbox efectivos, payload mínimo; PII histórica sólo en revisión privada del expediente |
| Tablas orientativas otorgan cartera completa al owner | D74-03/OQ-015: todos requieren capacidad y grant exacto; ninguna jerarquía o wildcard |

Estas discrepancias se registran en [D74/OQ](../product/SPEC01-decisions-74.md); las alternativas siguen pendientes. La enmienda v0.1.1 prevalecería sobre v0.1 **sólo tras aprobación expresa de sus cortes**. Notas históricas no conceden permisos.

## Dependencias verificadas de #34–45

Todas las issues #34–45 estaban abiertas al revisar. No se afirma ejecución por estar en el backlog.

| Issue | Alcance y dependencias leídas | Gate concreto |
|---|---|---|
| [#34](https://github.com/Dagt/Fiscuvia/issues/34) | API/modelo RFC; #26/#28; reutiliza #29/#32/#33 | D74-01..05; aprobación de corte mínimo #74 |
| [#35](https://github.com/Dagt/Fiscuvia/issues/35) | Directorio, búsqueda/filtros; #34/#52 | API lista autorizada; no adelantar directorio en #34 |
| [#36](https://github.com/Dagt/Fiscuvia/issues/36) | Ficha, contactos, responsables, certificados metadata; #34/#29/#35 | OQ-013/015/024; comandos de cartera y permisos propios |
| [#37](https://github.com/Dagt/Fiscuvia/issues/37) | CSV/Excel; #34/#46 | Upload seguro, dedupe tenant, preview/confirmación; posterior |
| [#38](https://github.com/Dagt/Fiscuvia/issues/38) | Plantillas versionadas; #34/RLS | OQ-001..006 y catálogo revisado |
| [#39](https://github.com/Dagt/Fiscuvia/issues/39) | Configuración/vigencia; #38/#34 | OQ-001/006/023; sin inferencia por régimen |
| [#40](https://github.com/Dagt/Fiscuvia/issues/40) | Ocurrencias idempotentes; #39/DB | OQ-001/002/004/023, autoridad de ejecución; #29 no es worker |
| [#41](https://github.com/Dagt/Fiscuvia/issues/41) | Calendario/lista/matriz; #40/#52 | Fechas/proyecciones autorizadas; referencia visual |
| [#42](https://github.com/Dagt/Fiscuvia/issues/42) | Alertas/vencimiento; #40/#29/#36 | OQ-023/024 y autoridad de jobs/notificaciones; sólo fechas |
| [#43](https://github.com/Dagt/Fiscuvia/issues/43) | Tareas, checklist, recurrencia; #27/#28/#34/#29 | Estado/tareas y permisos propios; sin cerrar declaración por tarea |
| [#44](https://github.com/Dagt/Fiscuvia/issues/44) | Revisión; #40/#29 | OQ-002/003/005 y separación de funciones |
| [#45](https://github.com/Dagt/Fiscuvia/issues/45) | Presentación/pago; #44/#46–48 | OQ-003/004; documentos AVAILABLE y procedencia; sin equivalencias SAT |

## Qué se puede decidir y qué sigue bloqueado

Las invariantes técnicas ya exigidas por AGENTS (UUID, tenant, FK compuestas, RLS, concurrencia, auditoría, sin secretos ni migraciones históricas editadas) pueden conservarse sin decisión arquitectónica nueva. Se define una propuesta implementable para #34; aún no es autorización de implementación.

El propietario debe aprobar o corregir **D74-01..05**: identidad/lifecycle RFC, régimen manual y procedencia, matriz fiscal mínima con bootstrap acotado, frontera #34/#36 y cuota TAXPAYERS. Debe registrar fecha, alcance, opción elegida y enlace verificable; una aprobación genérica del PR documental no instala grants.

El contador debe validar catálogo/periodicidad, estados y evidencia de cierre, complementarias/parcialidades, autoverificación, interpretación de regímenes y calendarios. Se puede solicitar aprobación expresa para diferir estos módulos y comenzar sólo #34 sin automatizaciones. OQ-001..006/013/015/023 permanecen Open en sus alcances; el experto no se sustituye por esta propuesta.

Para abrir el PR de implementación #34 faltan exactamente: aprobación explícita del corte D74-01..05 y del diferimiento descrito; actualización de documentos/contrato según respuesta; referencia de aprobación para futuras políticas; cuotas sintéticas administradas para pruebas; latest main/SHA nuevos y plan de migración aditiva revisado con las pruebas de esta especificación. No abrir otro PR #34 mientras esos puntos estén pendientes. Retención/producción/proveedor legal/OQ-008/014 siguen gates del piloto, sin bloquear por sí solos el trabajo local/test sintético aprobado.
