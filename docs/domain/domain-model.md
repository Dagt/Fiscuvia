# Modelo de dominio (conceptual, no esquema SQL)

**Estado:** Proposed; origen: requisitos FR y relato del despacho. Evitar modelar literalmente las casillas de un HTML o Excel; distinguir el trabajo operativo de evidencia oficial.

## Agregados principales
**Tenant (Despacho)**: suscripción, configuración, usuarios, política de seguridad, catálogo de plantillas; contiene clientes, no identidades fiscales oficiales de otros despachos.

**User / Membership / RoleAssignment**: identidad global OIDC, participación en uno/más despachos y capacidades/alcance por cartera. Operador SaaS separado.

**Taxpayer (Contribuyente)**: persona física/moral atendida por despacho, RFC normalizado (identidad externa, no PK), razón social, régimen efectivo, responsables, contactos, estado y expedientes. Mismo RFC en tenants distintos sigue siendo aislado.

**ObligationTemplate**: definición *configurable y versionada* del trabajo fiscal (frecuencia, política de evidencia/revisión, regla de vencimiento validada); no prueba que un contribuyente legalmente tenga una obligación.

**TaxpayerObligationConfig**: vincula plantilla vigente a contribuyente para períodos específicos y responsables.

**ObligationOccurrence**: unidad de seguimiento fiscal para un contribuyente + plantilla versión + período. Tiene **tres dimensiones**: avance de trabajo, presentación, pago fiscal. No sumar todo en un solo booleano `completed`. Puede originar Task y Evidence. Deadline es DATE en contexto jurisdiccional.

**Review/Decision**: acción autorizada con actor, fecha y razón; no atribuir aprobación de un auxiliar al SAT.

**TaxFilingEvidence / TaxPaymentEvidence**: referencias a documentos adjuntos, procedencia manual/verificada, valor, fecha y referencia. Guardar evidencia fiscal sin confundirla con la contabilidad o pagos de honorarios del despacho.

**Task**: unidad de trabajo asignada, puede referenciar cliente/obligación, no es sustituto de obligación.

**Document**: archivo privado, hash, metadatos, clasificación, validación y alcance de autorización; contenido binario en R2/S3.

**FeeAgreement/Charge/Payment/Adjustment**: cuentas por cobrar del **despacho** frente a sus clientes; independientes de impuestos SAT.

**AuditEvent**: eventos de accesos privilegiados y cambios sensibles, con actor, fuente, antes/después redactado, correlación y tiempo; inmutabilidad lógica.

**OutboxJob**: trabajo asíncrono durable, tenant autorizado, reintento, progreso y resultados. En fase futura soporta SAT/CFDI.

**CFDI Document / Download Request (futuro)**: XML original y metadatos del comprobante, UUID, estado con fuente y fecha verificada, descargar/consultar vía SAT sólo tras ADR de firma aprobado.

## Relaciones importantes
Tenant 1:N Taxpayer; User N:M Tenant mediante Membership; Membership N:M Taxpayer mediante assignment; Taxpayer 1:N ObligationConfig, Occurrence, Document, FeeCharge; Template(version) 1:N Occurrence; Occurrence 1:N ReviewDecision y Evidence; FeeCharge 1:N FeePaymentAllocation; Document puede vincularse a varias evidencias del mismo tenant con autorización explícita.

## Invariantes de dominio
- RFC no es tenant.
- Una declaración 'presentada' no implica tributo 'pagado' ni aceptación SAT.
- Evidencia manual debe tener fuente y revisión/validación si aplica.
- Corrección/reapertura produce evento; no borra historia.
- Recurrencia idempotente; versiones anteriores congelan reglas del período original.
- Estado de vencimiento se calcula, no es estatus editable destructivamente.
- Honorarios nunca se mezclan con pago de impuestos.
