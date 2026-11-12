# Dominio fiscal v0.1.1 — propuesta revisada

**Proposed; Refs #74; sin implementación.** [Esquema](../data-model/schema-v0.1.1-proposed.md), [decisiones pendientes](../product/SPEC01-decisions-74.md). Los estados siguientes son propuestas para revisión contable, no reglas de ley aprobadas.

## Límites y relaciones

```mermaid
erDiagram
    TENANT ||--o{ MEMBERSHIP : participa
    TENANT ||--o{ TAXPAYER : posee
    MEMBERSHIP ||--o{ TAXPAYER_ASSIGNMENT : responsabilidad
    TAXPAYER ||--o{ TAXPAYER_ASSIGNMENT : conserva
    TAXPAYER ||--o{ PROFILE_REVISION : historial
    PROFILE_REVISION ||--o{ REGIME_REFERENCE : captura
    TAXPAYER ||--o{ OBLIGATION_CONFIG : configura
    OBLIGATION_TEMPLATE ||--o{ TEMPLATE_VERSION : versiona
    OBLIGATION_CONFIG ||--o{ CONFIG_VERSION : versiona
    TEMPLATE_VERSION ||--o{ CONFIG_VERSION : referencia
    OBLIGATION_CONFIG ||--o{ OCCURRENCE : origina
    OCCURRENCE ||--o{ REVIEW_ATTEMPT : revisa
    OCCURRENCE ||--o{ FILING_RECORD : presentacion
    OCCURRENCE ||--o{ TAX_PAYMENT_RECORD : impuesto
    TAXPAYER ||--o{ DOCUMENT : expediente
    DOCUMENT ||--o{ DOCUMENT_VERSION : conserva
    TAXPAYER ||--o{ FEE_CHARGE : honorarios
    FEE_PAYMENT ||--o{ FEE_ALLOCATION : aplica
    FEE_CHARGE ||--o{ FEE_ALLOCATION : recibe
```

Todas las relaciones del diagrama son tenant-safe; las FK triple del esquema impiden además mezclar contribuyentes dentro de un tenant. No hay expediente global compartido por RFC. identity conserva identidad, membresías/roles y mecanismo de autorización; taxpayers conserva perfil e historial/responsabilidades; obligations configuración/ocurrencias; workflow revisión; tasks trabajo administrativo; documents objetos privados/versiones; fees cuenta por cobrar; audit journal/outbox actuales. Puertos públicos Modulith, sin acceso a internals ni nuevos servicios/colas.

## Contribuyente y régimen

Taxpayer representa una relación de servicio del despacho con persona física/moral. UUID interno estable y RFC comercial normalizado; nombre legal y comercial no son credenciales. ACTIVE/ARCHIVED es ciclo administrativo, jamás situación ante SAT. Archivo conserva revisiones, grants e historia, impide nuevas operaciones ordinarias/generación futura; restauración requiere permiso explícito y cuota, conserva ID. Lectura del archivado sigue requiriendo grant. No delete físico.

D74-02 propone conjunto de referencias de régimen, no un campo autoritativo único. Cada revisión guarda lo que el despacho capturó, fuente MANUAL, actor/instante, vigencia conocida o desconocida y revisión pendiente. Distintos regímenes pueden coexistir; ninguna referencia determina por sí sola obligaciones. El contador valida catálogo, códigos/vigencias reales y compatibilidades antes de usar datos como regla. No hay parsing CSF, consulta SAT, homoclave calculada ni fecha de verificación inventada.

## Responsabilidad y acceso

Assignment describe PRIMARY, CONTRIBUTOR o REVIEWER con inicio/fin e historial. Un rol supervisor no es una asignación REVIEWER ni una aprobación fiscal. Máximo un PRIMARY vigente; colaboradores adicionales múltiples tras OQ-013. La cartera autorizada proviene de capacidad por rol **y** grant explícito por membresía/recurso/operación, no de la relación laboral o el responsable mostrado.

En #34 se propone únicamente PRIMARY inicial para el owner creador; los cuatro grants exactos iniciales son efecto explícito del comando de alta revisado en D74-03, no regla automática para todos los owners ni para nuevas membresías. #36 diseñará UI/comandos separados para cambiar responsabilidad y para conceder/retirar acceso exacto, con auditoría de cada operación. Cambiar responsable no concede ni elimina permisos por inferencia. Si se desea combinarlos, será un comando explícito acotado con sus dos efectos declarados y pruebas, no listener implícito.

## Obligación, período y configuración

Template es identidad estable del tipo de trabajo; TemplateVersion congela periodicidad y políticas de evidencia/revisión/vencimiento con su origen/aprobación. Config asocia un contribuyente con esa identidad de obligación y mantiene versiones de vigencia; los cambios requieren previsualización de impacto. Occurrence identifica config estable+período [inicio,fin), conserva versiones exactas y vencimiento/fuente originales. Cambiar versión no crea una segunda ocurrencia del mismo período ni reescribe la ya emitida.

Periodicidades iniciales y definición de períodos (mensual, anual u otras) quedan OQ-001; no seed de fechas legales. Fecha de vencimiento LocalDate con procedencia manual o regla revisada, jurisdicción y zona de cómputo; excepción/prórroga como evento autorizado con causa. Proyección “próxima/vencida” no cambia estado ni demuestra incumplimiento legal. No asumir día17 ni mover por fin de semana automáticamente. Un taxpayer ARCHIVED no genera nuevas ocurrencias; conservar las anteriores no significa cancelarlas.

Generación requiere comando idempotente aprobado y autoridad humana actual o autoridad de servicio específicamente diseñada. El outbox sólo transporta auditEventId: no es un calendario, scheduler ni principal fiscal. Configuración/plantilla y locks del período se revisan antes de #40. Las tareas recurrentes tienen serie propia, no se confunden con recurrencia fiscal.

## Estados y transiciones para validar

| Agregado/dimensión | Propuesta | Guardia / efecto / bloqueo |
|---|---|---|
| Taxpayer | ACTIVE→ARCHIVED; ARCHIVED→ACTIVE | Archive/Restore, expectedVersion, permiso por expediente, audit; restore comprueba cuota. D74-01/03/05 |
| Trabajo de obligación | PENDING→IN_PROGRESS→IN_REVIEW→APPROVED; rechazo a IN_PROGRESS; reapertura motivada | StartWork/SubmitReview/Approve/Reject/Reopen; revisión/evidencia y separación de funciones. OQ-002/005 |
| Aplicabilidad | APPLICABLE / NOT_APPLICABLE como decisión separada del avance | MarkNotApplicable con fundamento, no cancelar presentación/pago histórico. OQ-002; armonizar work_state.NOT_APPLICABLE de v0.1 después de decisión |
| Revisión por intento | DRAFT→SUBMITTED→APPROVED o REJECTED; nuevo intento; SUPERSEDED con causa | No borrar aprobación/rechazo previo; aprobación referencia evidencias concretas; autor distinto salvo excepción aprobada. OQ-005 |
| Presentación | NOT_FILED / REPORTED_WITHOUT_EVIDENCE / FILED_RECORDED | Provisional sólo si OQ-003 lo permite; RecordFiling exige fecha/actor/procedencia/evidencia o excepción autorizada. No SAT_VERIFIED disponible en MVP |
| Pago fiscal | UNDETERMINED / NOT_REQUIRED / PENDING / PARTIALLY_RECORDED / PAYMENT_RECORDED | Comandos explícitos con base/evidencia, motivo si no requerido, pagos append-only. UNDETERMINED evita inferir PENDING/NOT_REQUIRED sin decisión; OQ-002/004 |
| Tarea administrativa | OPEN→IN_PROGRESS→IN_REVIEW→DONE; rechazo/reapertura, CANCELLED motivado | Política de revisión propia; DONE no presenta declaración ni liquida impuesto |
| Documento versión | PENDING_UPLOAD→UPLOADED→VALIDATING→AVAILABLE o REJECTED/QUARANTINED | Hash/MIME/scan y permiso; ARCHIVED lógico no elimina evidencia referenciada. #46–48/OQ-008 |
| Honorario/cargo | DRAFT→ISSUED; PARTIALLY_PAID/PAID derivados de movimientos; VOIDED con reversión autorizada | Saldo/vencido derivados, no UPDATE status libre; OQ-011 |

La máquina histórica [obligation](../state-machines/obligation.md) usa NOT_APPLICABLE dentro del trabajo y no tiene UNDETERMINED de pago; [tax-payment](../state-machines/tax-payment.md) añade parcialidad. La propuesta separa aplicabilidad y explicita desconocimiento; **requiere decisión OQ-002**, no reemplaza silenciosamente esos documentos. Hasta validación no crear constraints fiscales definitivos. “Definitivo propuesto” significa que las relaciones están preparadas para revisión, no que los nombres de estado estén aceptados.

## Evidencia, documentos y hechos oficiales

Presentación, recepción oficial, pago fiscal, revisión humana y cierre administrativo son hechos distintos. Un usuario puede registrar FILED_RECORDED+PENDING mientras trabajo sigue IN_PROGRESS por pendientes internos; no deducir un estado del otro. Si el flujo exige aprobación antes de presentar, una excepción debe ser explícita y auditable. Documentar fecha del hecho y fecha de captura por separado.

FilingRecord y TaxPaymentRecord apuntan a versiones documentales concretas AVAILABLE y del mismo contribuyente/ocurrencia. Corrección/complementaria crea registro nuevo vinculado; importes tributarios sólo hechos capturados, sin motor de liquidación o compensación. Document logical metadata, versión y objeto físico tienen lifecycle separado; clasificación no es permiso. Comprobante manual y fuente externa llevan procedencia diferente, ninguna es SAT verificado sólo por ser PDF/XML. Una futura fuente oficial necesitaría contrato de verificación/frescura/alcance y aprobación; no campo editable satVerified.

Certificados sólo tipo/fecha/fuente/estado de revisión/historial de renovación, no claves, .cer, .key ni contraseñas. Archivo administrativo y acuse fiscal se clasifican y autorizan de forma distinta; el permiso de subir no concede descargar/compartir.

## Honorarios y dinero

FeeAgreement define servicio/cadencia/vigencia; FeeCharge crédito del despacho por servicio recurrente o extraordinario; FeePayment ingreso registrado de **ese contribuyente**; Allocation aplica parcialidad; Adjustment/reversal conserva historia. Adeudo/aging se deriva de cargos, ajustes y aplicaciones netas. Cancelar o corregir conserva movimientos originales y motivo; sobrepago y escala/redondeo requieren OQ-011.

TaxPaymentRecord es impuesto del contribuyente y no participa en esos saldos. No “Payment” genérico compartido. Reportes y UI usan títulos distintos: “Honorarios del despacho” y “Pago de impuestos registrado”; no total combinado. No cobros, emisión CFDI/PAC, bancos ni facturación en #74/#34.

## Auditoría y revisión final de dominio

Cada mutación confirmada incluye perfil/historia/evento/outbox/recibo atómicos. Journal tipado sin RFC/nombres/documentos/bodies; detalle histórico privado dentro de expediente con autorización. Correcciones, archivo, reasignación y reaperturas no destruyen atribución. Decisiones de denegación siguen política privada vigente; no prometer auditoría de cada lectura de PII sin diseño/retención aprobados.

Trazabilidad: FR-012..031/040..065, BR-010..064, NFR-001..009/020..026/030..036; ADR-003/005/007/008/010/014/015 conservan Proposed. #34 puede aislarse únicamente con aprobación D74-01..05; #38–45/documentos/honorarios requieren las decisiones propias antes de migraciones definitivas.
