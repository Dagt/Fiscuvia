# Catálogo de requisitos funcionales — FR-### (propuesto v0.1)

**Estado:** Proposed. Requisitos de producto por validar/aprobar; no funciones ya implementadas. **M = MVP**, **P = posterior**, **F = futuro**. Basado en alcance Fiscuvia y metodología de catálogo de TrailerSaaS, no en funciones del sistema logístico.

## Identidad, tenant y autorización
- **FR-001 [M]** El sistema gestionará múltiples despachos (tenants) con separación estricta de datos.
- **FR-002 [M]** Un usuario autenticado deberá tener membresía activa para actuar en un despacho.
- **FR-003 [M]** Cada membresía podrá tener roles explícitos; permisos se evalúan en servidor.
- **FR-004 [M]** Las acciones sobre contribuyentes verificarán pertenencia y autorización por cartera.
- **FR-005 [M]** Los responsables del despacho podrán invitar, suspender y revocar colaboradores.
- **FR-006 [M]** El operador Fiscuvia no tendrá lectura habitual de expedientes fiscales.
- **FR-007 [M]** Todas las acciones sensibles serán trazables por actor, origen y momento.
- **FR-008 [P]** Los contribuyentes podrán acceder a portal con alcance individual.
- **FR-009 [M]** Las cuotas de plan se aplicarán a acciones y recursos en backend.

## Despachos, usuarios y contribuyentes
- **FR-010 [M]** Administrador configurará identidad visual básica y datos del despacho.
- **FR-011 [M]** Despacho gestionará su equipo, cargos/roles y responsables.
- **FR-012 [M]** Se podrán crear contribuyentes con razón social, RFC, persona física/moral, régimen y contacto.
- **FR-013 [M]** RFC se validará sintácticamente, sin afirmar confirmación oficial SAT.
- **FR-014 [M]** Un RFC podrá figurar en despachos distintos sin compartir datos.
- **FR-015 [M]** Los contribuyentes tendrán estatus activo/inactivo/archivado sin borrar historia.
- **FR-016 [M]** Podrán vincularse responsables del despacho a contribuyentes.
- **FR-017 [M]** Directorio tendrá búsqueda, filtros, paginación y exportación autorizadas.
- **FR-018 [M]** Expediente mostrará alertas y actividad reciente.
- **FR-019 [P]** Importación masiva desde CSV/Excel con validación y deduplicación.

## Obligaciones y calendario
- **FR-020 [M]** Despacho mantendrá catálogo de plantillas de obligaciones versionadas.
- **FR-021 [M]** Contribuyente tendrá configuraciones de obligación vigentes por fecha.
- **FR-022 [M]** Generación periódica será idempotente y conservará versión origen.
- **FR-023 [M]** Vencimientos serán configurables/revisables; no se inventará calendario SAT.
- **FR-024 [M]** Obligaciones tendrán responsables, fechas y estados controlados.
- **FR-025 [M]** Sistema presentará calendario/lista/matriz mensual coherentes.
- **FR-026 [M]** Existirán alertas de vencimiento/atrasos y filtros por período.
- **FR-027 [M]** Se separarán estados de trabajo, presentación y pago.
- **FR-028 [M]** Se podrá adjuntar evidencia del acuse de presentación.
- **FR-029 [M]** Se podrá registrar pago fiscal con evidencia independiente cuando aplique.
- **FR-030 [M]** Los cierres/reaperturas exigirán motivo, privilegio y auditoría.
- **FR-031 [M]** Vencimiento e.firma/CSD se registrará como fecha y metadata; sin llaves.
- **FR-032 [P]** Paquetes oficiales de reglas se versionarán tras revisión profesional.

## Tareas y supervisión
- **FR-040 [M]** Tareas contarán con cliente opcional, responsable, prioridad y fecha.
- **FR-041 [M]** Tareas recurrentes se generarán de forma idempotente.
- **FR-042 [M]** Personal podrá añadir checklist, comentarios y documentos autorizados.
- **FR-043 [M]** Envío a revisión/rechazo/aprobación quedará auditado.
- **FR-044 [M]** Rol/estado determinará acciones válidas, no PATCH libre de estados.
- **FR-045 [P]** Solicitudes de documentación y escalaciones serán automáticas.

## Documentos
- **FR-050 [M]** Se subirán archivos permitidos en almacenamiento privado con límites.
- **FR-051 [M]** Archivos se clasificarán por contribuyente, tipo y período.
- **FR-052 [M]** Acceso/descarga requerirá autorización y acceso de duración acotada.
- **FR-053 [M]** Cada archivo tendrá origen, autor, hash y estado de procesamiento.
- **FR-054 [M]** Habrá filtros, búsqueda y descarga con permisos.
- **FR-055 [M]** Archivos maliciosos/inválidos se rechazarán o pondrán en cuarentena.
- **FR-056 [P]** Portal permitirá solicitudes, cargas y entregables específicos al contribuyente.

## Honorarios y cobranza del despacho
- **FR-060 [M]** El despacho configurará cuotas recurrentes por contribuyente.
- **FR-061 [M]** Podrán crearse cargos extraordinarios y ajustes auditables.
- **FR-062 [M]** Se registrarán cobros parciales y completos asociados a cargos.
- **FR-063 [M]** Saldos se calcularán con decimales, no flotantes.
- **FR-064 [M]** Antigüedad y adeudos figurarán en dashboard/reportes.
- **FR-065 [M]** Honorarios serán independientes de impuestos del contribuyente.
- **FR-066 [P]** Facturación/conciliación bancaria de honorarios requerirá integración específica.

## Dashboard y reportes
- **FR-070 [M]** Dashboard mostrará clientes, atrasos, revisión, próximos vencimientos y cuentas por cobrar.
- **FR-071 [M]** KPIs incluirán origen/fecha y permitirán ir a detalles filtrados.
- **FR-072 [M]** Reportes ofrecerán filtros con autorización por cada fila.
- **FR-073 [M]** Se exportará a formatos CSV/Excel/PDF sin fuga de datos.
- **FR-074 [M]** Búsqueda global respetará tenant y asignaciones.
- **FR-075 [M]** Auditoría permitirá consultas autorizadas de cambios relevantes.

## CFDI, SAT y conciliación (fuera del MVP)
- **FR-080 [P]** El usuario podrá importar XML/ZIP CFDI autorizados y clasificar metadatos.
- **FR-081 [P]** Se identificarán CFDI emitidos/recibidos, UUID, comprobante, fechas, importes y complementos soportados.
- **FR-082 [P]** Habrá deduplicación y procedencia de estado de validación.
- **FR-083 [P]** Se buscarán y exportarán CFDI según permisos.
- **FR-084 [P]** Se podrán iniciar solicitudes oficiales SAT sólo con autorización y diseño de firma aprobado.
- **FR-085 [P]** Descargas SAT se ejecutarán como jobs persistentes con reintentos y cuotas.
- **FR-086 [F]** Se importarán movimientos bancarios en formatos expresamente soportados.
- **FR-087 [F]** La conciliación sugerirá coincidencias y exigirá revisión humana.
- **FR-088 [F]** Integraciones con CONTPAQi/terceros dependerán de interfaces oficiales y licencia.

## Portal y negocio
- **FR-090 [P]** Contribuyente verá sólo solicitudes y documentos autorizados.
- **FR-091 [P]** Contribuyente podrá entregar archivos y consultar entregables.
- **FR-092 [P]** Alertas externas se emitirán con consentimiento y preferencias.
- **FR-093 [P]** Suscripciones podrán cobrarse automáticamente cuando se apruebe proveedor.
- **FR-094 [P]** Suspensión por impago no eliminará de inmediato datos del cliente.
- **FR-095 [P]** Despacho podrá exportar datos según política de portabilidad.
