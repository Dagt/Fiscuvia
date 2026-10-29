# Experiencia Administrador del despacho — UX Flow v0.1 (Proposed)

**Actor:** FIRM_OWNER_ADMIN. **Refs:** FR-001..019, FR-060..075, BR-001..014, BR-060..064. **Seguridad:** tenant + cartera + capacidad.

## Navegación
Dashboard; Clientes; Obligaciones; CFDI (sólo Future flag); Tareas; Documentos; Honorarios; Reportes; Configuración, Miembros, Auditoría.

## Objetivo por pantalla
**Dashboard:** vista de carga, vencimientos, cuentas por cobrar y excepciones. Cada KPI debe dirigir a lista autorizada y filtrada. Prohibido total inventado o con fecha de estado simulada.
**Miembros:** listado rol/estatus/cartera, invitar por correo, revocar/suspender, asignar roles con descripción legible (no códigos de capacidades), confirmación de efecto, trazabilidad; un usuario suspendido conserva autoría.
**Clientes:** lista con razón social, RFC, régimen, responsable, estado/alerta; alta con campos claros, duplicados dentro del tenant y validación RFC sintáctica, sin decir SAT verificado.
**Ficha:** datos/expiración e.firma/CSD metadata, obligaciones, documentos, honorarios según permisos, actividad.
**Plantillas:** catálogo de obligaciones, periodicidad, vigencia, evidencia y revisión; se muestra versión y fuente, cambios no reescriben períodos anteriores.
**Honorarios:** cuotas/cargos/cobros; nunca mezclar con impuestos SAT.
**Auditoría:** registros read-only filtrados por actor/evento/fecha/recurso con datos redactados; 'ver más' restringido.

## Acciones sensibles
Cambiar responsable, suspender miembro, marcar no aplicable, reabrir período, ajuste de saldo: confirmación + motivo + resultado visible + audit; versión para evitar sobrescritura.

## Estados de UI
Loading, sin clientes, solo asignados, permiso denegado, invitación caducada, duplicado RFC, expiración de certificado próxima, cambio de membresía concurrente, cuotas excedidas. Navegación móvil via drawer, tablas como cards accesibles.

## No incluir
Gestión de llaves/contraseñas SAT, panel de contabilidad/pólizas, tickets de soporte con lectura automática de CFDI, fingir acceso a API SAT.
