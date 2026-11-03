# Especificación visual y funcional de pantallas

Inventario **conceptual**: no es avance funcional. [Layout/estados candidatos de las seis pantallas](reference-library/SCREENS-BASELINE.md), [rutas reales y auditoría](reference-library/CURRENT-UI-AUDIT.md). Hoy /app presenta sesión/selección de despacho, no dashboard. OQ-021 sigue Open y la referencia escrita requiere aprobación; original ausente.

1. **Login /login:** panel de marca Fiscuvia + acceso OIDC real, errores claros, móvil columna única. Credenciales/MFA/recuperación en el proveedor configurado; Fiscuvia no añade formulario de correo/contraseña propio. Card/composición exacta son propuestas de referencia, no autorización para cambiar BFF.
2. **Dashboard /app:** sidebar navy, header con búsqueda/período, KPI clientes activos, próximos/vencidos, revisión, honorarios; calendario semanal, distribución estados, actividad reciente. CFDI card sólo cuando exista módulo real. Cada KPI enlaza a vista filtrada.
3. **Clientes /app/taxpayers:** título, CTA Nuevo cliente, búsqueda, tabs activos/inactivos, filtros régimen/responsable, tabla clara razón social/RFC/régimen/responsable/estatus/alertas, paginación. Estatus autorizados.
4. **Detalle cliente /app/taxpayers/:id:** encabezado razón social/RFC/responsable, tabs Resumen/Obligaciones/Documentos/Honorarios/Historial; alertas vencimiento e.firma/CSD (no secretos), pendientes, actividad. CFDI y Declaraciones sólo al entregarse.
5. **Obligaciones /app/obligations:** vistas calendario/lista/matriz, selector período/cliente/responsable/estado, detalle lateral, evidencia y etapas de presentada/pagada SEPARADAS, vista lista accesible.
6. **Tareas /app/tasks:** mis tareas, equipo, fechas, urgencia, revisión, checklist y comentarios.
7. **Documentos /app/documents:** filtros, carpetas por contribuyente/tipo, carga segura, preview, historial y descarga autorizada.
8. **Honorarios /app/fees:** esperado/cobrado/vencido, antigüedad, cargos y pagos del despacho (distintos de impuestos SAT).
9. **Reportes /app/reports:** plantillas, filtros, exportación con estado de job y audit.
10. **Configuración /app/settings:** datos despacho, miembros, permisos, plantillas y plan.
11. **CFDI /app/cfdi (posterior):** emitidos/recibidos, filtros, totales, estado autenticado y fecha, tabla+panel, XML/Excel.
12. **Portal contribuyente (posterior):** UX reducida, subir solicitudes y consultar entregables autorizados.
### Estados de TODAS las pantallas
Loading, primera vez, lista vacía filtrada, error recuperable, sin permiso, validación, nombres largos, cifras grandes, desktop 1440px, mobile 390px, teclado. No entregar sólo la tabla principal.
