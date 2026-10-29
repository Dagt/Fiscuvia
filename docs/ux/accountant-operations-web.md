# Contador, supervisor y auxiliar — UX Flow v0.1 (Proposed)

**Actores:** FIRM_SUPERVISOR, ACCOUNTANT, ASSISTANT. **Refs:** FR-020..056, BR-020..054, estados.

## Inicio del día
Seleccionar despacho autorizado y período -> dashboard con 'Mis pendientes', 'Por vencer', 'En revisión' -> abrir cliente o obligación -> siguiente acción permitida. Mostrar estado explícito; contar solamente elementos autorizados.

## Calendario mensual
Vistas calendario, lista y matriz obligatoria: columnas período/tipo/responsable/estado preparación/presentación/pago/vencimiento/evidencia. El color no reemplaza el texto. Fecha vencida se deriva y puede coexistir con presentación registrada. Filtros URL persistentes por responsable/cliente/tipo/fecha.

## Preparación y revisión
Detalle de obligación: encabezado con contribuyente/RFC, período, dueño y vencimiento; checklist con documentos; botón Preparar / Enviar a revisión; para supervisor Aprobar/Rechazar; acuse y pago por módulos separados. Timeline conserva cambios, motivos y origen de evidencia. No PATCH arbitrario de estatus.

## Documentos
Subir->validación/escaneo->disponible->enlazar a obligación. Mostrar cuándo un documento no está listo para usarse como evidencia; ninguna descarga sin autorización. PDF preview opcional y accesible.

## Tareas
Mis tareas, equipo por supervisor, prioridad, fechas, checklists, comentarios, asignación; tareas no se confunden con obligaciones. Altas repetidas por periodicidad idempotente.

## Microcopy
No usar 'completada' indiscriminadamente; usar 'Trabajo aprobado', 'Presentación registrada', 'Pago registrado', 'Pendiente de comprobante', 'No verificada por SAT'. Proveer '¿Qué significa este estado?' con origen.

## Estados
No hay tareas, vencimiento hoy, sin documentos, error subida, revisión rechazada, conflicto 409, pérdida de acceso, rango sin registros, cientos de contribuyentes y matriz horizontal. Desktop permite alta densidad; móvil tareas/alertas y tarjetas, sin tablas ilegibles.
