# Reglas de negocio — BR-### (propuesto v0.1)

**Estado:** Proposed; requiere validación de contadores y propietario antes de declararse Accepted.

## Identidad
- **BR-001** Un tenant es el despacho que paga Fiscuvia; un contribuyente no es tenant.
- **BR-002** Un RFC puede repetirse en tenants diferentes y los expedientes no se compartirán.
- **BR-003** Ninguna identidad autenticada adquiere acceso fiscal por existir; requiere membresía, capacidad y alcance.
- **BR-004** Un miembro suspendido conserva atribución histórica pero no acceso operativo.
- **BR-005** Soporte Fiscuvia no tiene acceso al expediente salvo excepción autorizada, caducable y auditada.

## Expediente
- **BR-010** RFC es identificador de negocio, nunca PK; estado oficial no inferible sólo de formato.
- **BR-011** Cambios de régimen/obligación deben conservar vigencia temporal, no sobrescribir histórico.
- **BR-012** Contribuyente archivado no puede generar nuevas obligaciones automáticamente.
- **BR-013** Campos sensibles y documentos se muestran sólo a roles autorizados.
- **BR-014** Expiración de certificado se almacena como metadata; no e.firma/clave privada en MVP.

## Obligación y período
- **BR-020** Plantilla de obligación tiene versión y política de frecuencia/evidencia.
- **BR-021** Períodos y vencimientos requieren configuración explícita/revisión profesional; no cálculo normativo inventado.
- **BR-022** Misma plantilla-version/cliente/período no crea dos ocurrencias al reintentar.
- **BR-023** Cambio de plantilla no reescribe obligaciones anteriores de forma silenciosa.
- **BR-024** Trabajo completado, declaración presentada y tributo pagado son dimensiones distintas.
- **BR-025** 'Presentada' describe evidencia registrada por colaborador; no equivale a confirmación SAT.
- **BR-026** 'Pagada' requiere evidencia de pago según política; no deriva automáticamente de 'presentada'.
- **BR-027** Declaración sin impuesto a pagar puede no requerir flujo de pago; registrar razón.
- **BR-028** Una obligación puede ser No aplicable sólo con actor, motivo y política autorizada.
- **BR-029** Vencido se deriva de fecha/estado con una zona/jurisdicción definida; no borra el estado de trabajo.
- **BR-030** Reabrir/corregir obliga a conservar transiciones y causa.

## Tareas/revisión
- **BR-040** Tarea, obligación y expediente son entidades relacionadas pero no idénticas.
- **BR-041** Roles y estados delimitan aprobar/rechazar; un autor no aprueba su propio trabajo si política de separación está activada.
- **BR-042** Rechazo preserva motivo, actor y documentos anteriores; no elimina trazabilidad.
- **BR-043** Actividad recurrente es idempotente e independiente de la cadencia fiscal salvo vínculo definido.

## Documentos
- **BR-050** Archivo es privado desde su creación; descarga verifica autorización de objeto.
- **BR-051** Documento tiene hash/contenido original y revisiones metadata auditables.
- **BR-052** Upload pendiente/invalidado no se muestra como evidencia definitiva.
- **BR-053** Expiración del link no modifica derecho de acceso; se puede solicitar nuevo link autorizado.
- **BR-054** El período/cliente asociado a un documento no puede apuntar a otro tenant.

## Honorarios
- **BR-060** Honorarios son créditos del despacho contra cliente; no son obligación fiscal SAT.
- **BR-061** Pago parcial reduce saldo de un cargo; sobrepago requiere decisión explícita.
- **BR-062** Corrección/reversión de cobro es movimiento auditado; no eliminación destructiva.
- **BR-063** Cantidades MXN se representan con precisión decimal definida y reglas de redondeo explícitas.
- **BR-064** Cargo recurrente debe ser idempotente por plan/cliente/período.

## CFDI/SAT futuros
- **BR-070** Ingesta XML requiere autorización y no garantiza status SAT actual.
- **BR-071** CFDI se identifica por UUID+scope; conflictos de metadatos no se silencian.
- **BR-072** Cancelaciones/validaciones necesitan fuente y timestamp; no mostrar Vigente sin origen.
- **BR-073** No se custodian contraseñas o llaves e.firma hasta ADR, legal y gate aprobados.
- **BR-074** Descarga masiva SAT se ejecuta con throttling, persistencia y reintentos; no es instantánea.
- **BR-075** Conciliación propuesta requiere aceptación/rechazo humano con auditoría.

## SaaS
- **BR-080** Suspensión por cobro no borra datos sin cumplir política contractual/retención.
- **BR-081** Cambios de plan no rompen restricciones de integridad ni filtros tenant.
- **BR-082** Exportación/portabilidad respeta rol/tenant y términos de tratamiento de datos.
