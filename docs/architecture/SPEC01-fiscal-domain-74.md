# SPEC01 — modelo fiscal y preparación de #34

**Documento histórico del gate #74:** #92 fue integrado por el propietario. La implementación posterior #34 se documenta en [C01/modelo efectivo V12](TAXPAYER-DOMAIN-34.md); las referencias de esta revisión a ausencia de código/políticas se refieren al PR documental #92, no al estado de la rama C01.

**Estado: D74-01..05 aprobadas con alcance limitado de #34 por Dagt el 2026-11-12; resto del dominio fiscal Proposed.** Refs #74. [Fuente de aprobación y restricciones](../decisions/issue-74-owner-approval.md). Se incorporan decisiones recibidas sobre HEAD `260c53202a03a80597769f6fab80e348b0e437bd`, en la misma rama/PR #92. No implementa #34 ni habilita grants/migraciones en esta tarea; OQ completas Open. Presupuesto $0, sin merge/Ready/despliegue.

## Lectura y resultado de la revisión

Base actualizada mediante fetch, switch main y pull --ff-only: `05dc33e77e290777c316d26edabb7ce1e7b28c44`, padre `19d6ec043ccbfda3c75c39b3e5428a72666cd3ba`. Último merge: [PR #91, equipo #33](https://github.com/Dagt/Fiscuvia/pull/91), commit del 2026-11-11. Rama documental: `issue-74-fiscal-domain-specification`.

Se revisaron AGENTS/README/mapa documental, discovery/evidence-matrix, PRD/MVP/FR/BR/OQ, actores/capacidades, ADR-001/003/005/006/007/008/009/010/011/014/015, workflows/estados, esquema/API, UX, backlog/DoD/CI, código identity/tenants/entitlements/audit y Flyway V1–V11. Se consultaron íntegramente las issues [#74](https://github.com/Dagt/Fiscuvia/issues/74), [#18](https://github.com/Dagt/Fiscuvia/issues/18) y #34–45. #74 tiene cero comentarios en esta revisión. #18 permanece abierta: no hay validación profesional documentada que permita aprobar reglas fiscales.

La actualización confirma origin/main en el mismo SHA de partida, sin cambios productivos que integrar. El propietario aprobó el corte inicial y los diferimientos; el control de habilitación versionada sustituye expresamente la alternativa anterior que podía recuperar grants al reactivar o volver a un rol. Las decisiones administrativas de V1–V11 se conservan.

Entregables para revisión final:

| Documento | Responsabilidad |
|---|---|
| [Esquema v0.1.1](../data-model/schema-v0.1.1-proposed.md) | Enmienda relacional, restricciones, RLS y evolución por módulo |
| [Dominio revisado](../domain/fiscal-domain-v0.1.1.md) | Agregados, estados, obligaciones, documentos y honorarios |
| [Permisos](../security/FISCAL-PERMISSIONS-PROPOSAL-74.md) | Matriz propuesta y frontera con V11 |
| [Decisiones/OQ](../product/SPEC01-decisions-74.md) | Decisiones aprobadas, análisis conservado y OQ restantes |
| [Registro de aprobación](../decisions/issue-74-owner-approval.md) | ID/fecha/autoridad/alcance/restricciones/dependencias/fuente |
| [Especificación #34](../api/taxpayers-34-specification.md) | Contratos, transacciones, errores, pruebas y frontera UI |
| [OpenAPI #34 no implementado](../api/openapi-taxpayers-34-proposed.yaml) | Contrato aislado y verificable; no genera cliente productivo |
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

| Hallazgo en v0.1 / documento histórico | Tratamiento: aprobado #34 o propuesto posteriormente |
|---|---|
| Identidad propuesta no coincide con V3; tenant propuesto usa id en lugar de tenant_id | Usar base física V1–V11, no migrar de nuevo identidad/tenant |
| UNIQUE parcial RFC activo permite dos expedientes históricos del mismo RFC | D74-01 aprueba UNIQUE tenant+RFC para toda la vida del expediente; restaurar el mismo UUID |
| FR-015/borrador API incluyen INACTIVE; #34 pide ACTIVE/ARCHIVED | D74-01 aprueba dos estados para #34; INACTIVE diferido, sin interpretar baja fiscal SAT |
| Régimen singular y prohibición genérica de solapamiento | D74-02 aprueba conjunto de referencias con vigencia, admite regímenes distintos simultáneos; no catálogo fiscal inventado |
| Responsable duplicado en taxpayers y assignments | Una relación temporal; no columna primary_owner que pueda divergir |
| resource_grants referencia UUID genérico sin FK al recurso real | Columna generada nullable por tipo + FK compuesta futura; no registro paralelo de autoridad |
| Alta exige asignación a recurso que todavía no existe | D74-03: TAXPAYER_CREATE existente, target TENANT, función estrecha; grants iniciales exactos en el mismo commit, lectura posterior reautorizada |
| Capacity acepta sólo MEMBERSHIP_INVITE y reservations.reference_id apunta a users | #34 necesita productor TAXPAYERS propio, no pasar taxpayerId por reserva USERS ni fingir medición |
| Unicidad de ocurrencia incluye versión de plantilla: cambio de versión puede duplicar período | OQ-001/004: identidad estable de obligación configurada + período; versión es procedencia congelada, no identidad de recurrencia |
| document_links polimórfico carece de FK concreta y relación mismo contribuyente | Enlaces tipados con FK tenant+taxpayer+target; sin destino UUID libre |
| Borrador API usa Bearer y allOf cerrado incompatible; usa total supuesto | Contrato #34 aislado de sesión BFF, schemas cerrados sin allOf, PageResponse con hasNext |
| Tablas fiscales v0.1 sugieren audit JSON/outbox genérico | Reutilizar journal/outbox efectivos, payload mínimo; PII histórica sólo en revisión privada del expediente |
| Tablas orientativas otorgan cartera completa al owner | D74-03/OQ-015: todos requieren capacidad y grant exacto; ninguna jerarquía o wildcard |

Estas discrepancias se registran en [D74/OQ](../product/SPEC01-decisions-74.md): el corte de contribuyentes D74-01..05 está aprobado y prevalece sobre alternativas históricas de v0.1. Obligaciones/documentos/pagos/honorarios y sus estados siguen Proposed. Las notas históricas no conceden permisos; nada se instala en este PR. Revisión/versionado de grants impide recuperar acceso automáticamente tras cambios administrativos.

## Dependencias verificadas de #34–45

Todas las issues #34–45 estaban abiertas al revisar. No se afirma ejecución por estar en el backlog.

| Issue | Alcance y dependencias leídas | Gate concreto |
|---|---|---|
| [#34](https://github.com/Dagt/Fiscuvia/issues/34) | API/modelo RFC; #26/#28; reutiliza #29/#32/#33 | D74-01..05 aprobadas; revisión final/integración #92, siguiente tarea #34 |
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

## Decisiones incorporadas y siguiente paso

D74-01 aprueba identidad/RFC/lifecycle y rechaza sólo genéricos documentados por igualdad; corrección futura auditada y excepciones contables fuera de #34. D74-02 aprueba hasta10 referencias manuales MANUAL/PENDING_REVIEW con historia/vigencia conocida o desconocida, sin efectos fiscales automáticos. D74-03 aprueba matriz inicial y bootstrap exacto, sin cartera global ni delegación, y exige revisión de habilitación tras cambios de membresía/rol. D74-04 separa API #34, directorio #35 y ficha/cartera #36; D74-05 aprueba cuota sólo ACTIVE, archivo libera y restore/alta serializados. Fecha/autoridad/restricciones/dependencias y origen constan en el registro enlazado; no se vuelven a solicitar decisiones ya adoptadas.

El diseño técnico de grant_reviews vincula cada habilitación al membership.version/rol vigentes y verifica su última decisión en autorizador y RLS. V11 ya avanza esa versión al cambiar estado/rol; no se cambia ahora su administración, sus cuotas ni sus endpoints. Conservar grant histórico no conserva habilitación vigente. #34 sólo registra habilitaciones de bootstrap; revisión/gestión de cartera antigua queda #36 con política concreta por aprobar. Una membresía reactivada seguirá sin acceso a grants obsoletos hasta revisión explícita; ningún replay los renueva.

El propietario autorizó expresamente diferir OQ-001..005/023 y lo restante de OQ-006/013/015. No se marcan globalmente Resolved. Contador pendiente para obligaciones/periodicidad, estados/evidencia fiscal de cierre, complementarias/parcialidades, autoverificación, catálogo/semántica fiscal de regímenes y calendario legal. Estas preguntas no vuelven a bloquear la captura administrativa ya aprobada de #34; sí bloquean sus módulos correspondientes. Producción OIDC/MFA/correo, custodia e.firma y retención/piloto conservan gates propios.

La especificación inicial de #34 queda completa y autorizada en su alcance, sin funcionalidad entregada. Faltan revisión final y merge de #92 por el propietario; **no iniciar #34 en esta tarea**. Después, crear su rama/PR desde latest main, registrar SHA, materializar política exacta y habilitación versionada mediante migración aditiva revisada, configurar cuotas sintéticas administradas y ejecutar pruebas PostgreSQL/upgrade/RLS/OIDC/concurrencia/sinks y CI del nuevo SHA. No nueva aprobación de D74-01..05 salvo ampliación o cambio de alcance; no migración fiscal definitiva de módulos diferidos.
