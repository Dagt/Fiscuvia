# C02 #35 — directorio de contribuyentes

Base verificada: `518bc327173c355b2e6f843442f74248b980ff5c`, merge humano de PR #93. Alcance FR-017, NFR-030..036, ADR-004/011 y decisiones [D74-01..05](../decisions/issue-74-owner-approval.md). Implementación sujeta a revisión del PR; ninguna regla fiscal posterior se aprueba aquí.

## Navegación y composición

`/app/taxpayers` comparte la instancia de SessionPage, selección autorizada, AppShell y Navigation de `/app`. El contexto muestra exclusivamente el nombre comercial entregado en `/me`. Entrada directa sin selección abre el selector real; 401 vuelve a login y 403 fiscal muestra PermissionState, nunca una tabla vacía. No hay otra aplicación, biblioteca de componentes o sistema visual.

Se reutilizan PageHeader, Button, Field, FilterBar, DataTable, StatusPill, SideDrawer, ConfirmationDialog, estados compartidos y NotificationProvider. Navy/teal, Inter, Lucide y tokens #52. Tabla de seis columnas en escritorio; tarjetas por debajo de 1024 px. Filtros adicionales se pliegan junto al control compartido en móvil. Nombres envueltos, RFC legible, estado expresado con texto. No hay KPIs, conteos globales ni responsable supuesto.

El panel administrativo contiene identificación, nombre comercial, estado, referencias manuales, incorporación y última modificación reales. Alta/edición agrupadas en identificación y referencias, con fechas vacías para vigencia desconocida. La ficha 360°, contactos, obligaciones, documentos, honorarios, timeline y gestión de cartera siguen en #36 o historias posteriores; no existe `/app/taxpayers/:id` como ficha 360°.

## Consulta, filtros y paginación

El cliente TypeScript deriva sus DTO del OpenAPI implementado y valida las respuestas cerradas antes de renderizar. Consume búsqueda `search` por RFC/nombre, `status=ACTIVE|ARCHIVED`, `regime` vacío o exactamente tres dígitos, `page=0..10000`, `size=1..100` y `sort=id|legalName|createdAt,asc|desc`. La interfaz ofrece tamaños 10/20/50/100 y los seis órdenes permitidos. Las referencias de régimen son códigos manuales; no se provee ni inventa catálogo SAT.

Búsqueda y régimen usan debounce de 300 ms, AbortSignal y claves de consulta por identidad, tenant, membresía y parámetros. Cambiar búsqueda/filtro/orden/tamaño reinicia página. El servidor aplica RLS y filtros antes de LIMIT/OFFSET, obtiene size+1 y devuelve hasNext. El pie muestra página y registros de esa página; no calcula total global. Una página vacía posterior a mutación retrocede hasta una válida. Offset no es snapshot: altas simultáneas pueden mover filas; orden secundario por UUID evita empates inestables dentro de la misma consulta.

**Responsable diferido:** D74-03 no autoriza que TAXPAYER_VIEW revele el directorio de identidades ni las asignaciones históricas. No existe contrato seguro de opciones PRIMARY/nombres para todos los lectores fiscales. No se ofrece un filtro o columna ficticia, ni se reutiliza TENANT_USERS_MANAGE. #36 deberá definir/aprobar su proyección y opciones acotadas a expedientes visibles, antes de añadir el filtro. No bloquea el resto del directorio.

## Presentación de acciones

Se añade una proyección de lectura cerrada para este módulo, [contrato](../api/taxpayer-directory-35.md): colección `/presentation` y recurso `/{taxpayerId}/presentation`. La autorización VIEW existente se ejecuta en backend; la proyección comprueba política CREATE o grants EDIT/ARCHIVE vigentes, rol único, versión revisada de membresía y MFA según D74-03. Para alta comprueba capacidad real sin devolver conteos ni expedientes ocultos. No es un endpoint genérico de permisos y no instala grants/capacidades/políticas.

La UI muestra «Nuevo contribuyente» únicamente con canCreate confirmado. No usa el texto owner como autoridad. Si falta MFA o cuota muestra el motivo seguro comunicado por backend; una señal fallida no habilita el botón. Edición/archivo/restauración dependen de señales del expediente y versión coincidente con GET. Las señales son temporales: **todos los comandos vuelven a autorizar y comprobar cuota/versiones**. Restaurar puede mostrarse disponible por permiso y aun ser rechazado por cuota; el diálogo lo explica. Auxiliar no accede al módulo.

## Mutaciones y recuperación

Se consumen POST colección, GET recurso, PATCH perfil y POST archivals/restorations de #34. Datos enviados: únicamente identificación de alta, perfil permitido, referencias manuales, expectedVersion y motivos cerrados. RFC/persona son inmutables en edición. tradeName, validFrom y validTo desconocidos se envían explícitamente NULL. No se envían tenant interno, actor, timestamps, grants ni verificación SAT.

Validación de ayuda replica estructura local RFC, fecha representada, longitud/persona, caracteres aprobados Ñ/& y exclusión de los dos RFC genéricos exactos. No consulta SAT. Las referencias admiten cero a diez entradas, tres dígitos e intervalos semiabiertos `[inicio,término)`; no permiten duplicados/solapamientos del mismo código ni término sin inicio. Mensajes visibles: «La validación del RFC es local y no confirma su inscripción ante el SAT» e «Información capturada manualmente, pendiente de revisión». Backend conserva la validación definitiva.

Cada intento guarda un UUID idempotente y copia inmutable del comando en memoria. Error de red, 5xx o respuesta de éxito malformada significa resultado incierto: formulario bloqueado, panel retenido y «Recuperar resultado» reenvía exactamente cuerpo y clave. No se declara fallo definitivo ni se crea otro comando. Un resultado confirmado invalida consultas/listado/cuota/detalle y sólo entonces notifica éxito. No hay actualización optimista del estado fiscal. El resultado incierto se conserva mientras exista esa vista y contexto; salir de la sesión/cambiar tenant/recargar descarta memoria por privacidad. Recuperación persistente entre sesiones queda fuera del contrato actual; antes de repetir un alta tras recargar debe consultarse el directorio o pedir revisión autorizada del RFC.

409 STALE_VERSION conserva borrador y muestra «Actualizar datos para revisar»; recargar obtiene datos/versión antes de permitir otra edición. 409 CONFLICT de alta comunica posible RFC ya registrado, incluso archivado, sin exponer otro UUID. 422 permite corregir campos; cuota agotada no simula restauración. 404 retira el detalle no disponible; 401/403 retiran datos protegidos y notificaciones. No se reflejan textos arbitrarios del servidor.

Archivo confirma historial y reserva del RFC y usa SERVICE_ENDED/DUPLICATE_REVIEW/OTHER. Restauración confirma capacidad y usa SERVICE_RESUMED/CORRECTION. Las operaciones mantienen auditoría/outbox transaccionales #34; la UI no escribe auditoría propia ni duplica eventos al recuperar resultados.

## Privacidad y cambio de contexto

Selección/identidad/membresía determinan el montaje del módulo. Cambiar despacho, salir o invalidar sesión desmonta formularios, aborta consultas/comandos, elimina cachés del contexto y avisos; los resultados tardíos no montan datos en otra identidad. Consultas fiscales usan gcTime=0, sin persistencia ni placeholderData anterior. RFC/nombres/búsquedas no entran en rutas de navegación, localStorage, sessionStorage, analítica o logs. Los parámetros HTTP de consulta se envían al backend, nunca se incorporan a la URL de navegación del usuario. El backend conserva sus rutas de observabilidad con plantillas y no registra valores de filtros.

CSRF se obtiene con el mecanismo existente y los requests sólo admiten BFF same-origin, cookies, no-store, redirect:error y no-referrer. Las señales de presentación tienen Cache-Control:no-store. No se almacenan credenciales SAT o datos reales en fixtures/capturas.

## Estados y cobertura

| Estado | Evidencia principal |
|---|---|
| Vacío, listado, nombres largos, búsqueda sin coincidencia, filtro, orden, hasNext | TaxpayerDirectory.test + recorrido propietario OIDC |
| Cargando, debounce/cancelación, resultado tardío, cambio de tenant | unitarias y recorrido multitenant OIDC con respuesta real retenida |
| Error recuperable, respuesta perdida | red abortada después de backend real; recuperación idempotente OIDC |
| Sin permiso, auxiliar, sin grants | lectura denegada real de PostgreSQL/OIDC; no mock de capacidad |
| Sesión vencida | logout real y siguiente consulta 401; pruebas OIDC de sesión existentes |
| Formulario inválido, RFC, fechas, duplicados/solapamiento | validación/formulario Vitest, HTTP #34 y formulario real |
| RFC duplicado | comando real 409, sin datos del registro existente |
| Alta, edición, archivo y restauración confirmados | flujo Playwright contra backend/PostgreSQL/Keycloak |
| Conflicto de edición | segundo PATCH real mientras permanece abierto el formulario |
| Cuota agotada, restauración rechazada | límite sintético 1 y segundo contribuyente activo real |
| MFA insuficiente | sesión sintética expirada en fixture privado, señal/denegación backend |
| Contador con grants y grants obsoletos | invitación/aceptación real, revisión explícita sólo fixture, suspensión/reactivación real sin renovar grants |
| Mismo RFC en dos despachos | altas reales y cambio de contexto con respuesta A retenida |

Los controles privados del harness sólo existen en test-classpath y archivos ignorados, están restringidos a tenants sintéticos de este escenario y no crean endpoints productivos. Las pruebas anteriores se conservan. [Validación y comandos](../delivery/VALIDATION-35.md). [Capturas reales, hashes y comparación](evidence/issue-35/README.md).

Accesibilidad: etiquetas/asociación de errores, semántica de tabla, carga anunciada, botones de 44 px, foco visible y de retorno al cerrar panel, foco inicial de cancelar en confirmación, Escape y foco atrapado por Radix, estilos de reduced-motion existentes. Axe y revisión de reflow son evidencia parcial; no certificación WCAG.
