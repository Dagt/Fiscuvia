# Flujos de interacción
## Alta contribuyente
Clientes -> Nuevo -> RFC y datos, régimen y responsable -> validación formato RFC (no validación SAT) -> guardar -> expediente. Duplicado en tenant: aviso y resolución sin sobrescribir.
## Obligación mensual
Seleccionar plantilla revisada -> elegir contribuyente/periodo -> previsualizar vencimientos -> confirmar generación idempotente -> crear responsables/tareas. Si regla requiere verificación, etiqueta 'Fecha por validar'.
## Revisión y pago
Pendiente -> En proceso -> En revisión -> Presentada con acuse adjunto (registro interno, salvo validación externa) -> Pago pendiente -> Pagada con comprobante. Rechazar devuelve con motivo. Nunca mezclar impuestos y honorarios.
## Documentos
Subir -> validar/quarantena -> disponible -> acceso según permisos, hash, auditoría, error y reintento. Exportación siempre con controles de acceso.
## Cobranza despacho
Cargo -> registrar pago parcial/total -> recalcular saldo -> auditar; corrección como nuevo ajuste, sin borrado silencioso.
## Microcopy ES-MX
'Nuevo cliente', 'Registrar pago', 'Adjuntar acuse', 'Enviar a revisión'; estados claros, fuente y fecha; no usar 'verificado por SAT' sin consulta oficial.
