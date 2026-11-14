# C03 #36 — ficha del contribuyente

Base verificada `05f1d0b58783438e193ae9ad86e7181b0e62c135`, merge humano #94. FR-018/031, BR-011/014, NFR-031, D74-01..05 y componentes #52. Entrega parcial deliberada por autorización: este documento describe comportamiento implementado; [D36-01..03](../decisions/issue-36-approval-proposal.md) y [contratos adicionales](../api/taxpayer-360-36-proposed.md) continúan Proposed.

## Navegación y composición efectiva

`/app/taxpayers/:taxpayerId` comparte SessionPage, selección de despacho, AppShell, navegación y sesión del directorio. «Abrir ficha completa» en el panel administrativo enlaza al UUID del servidor. Entrada directa exige seleccionar un despacho real antes de consultar; volver al directorio conserva ese contexto. Cambiar despacho desde la ficha reemplaza la ruta por `/app/taxpayers`, retira el UUID anterior y desmonta sus formularios/consultas.

La composición sigue la referencia escrita S04: regreso al directorio, encabezado destacado con identificación/estado y acciones contextuales, navegación interna, columna principal con información y referencias manuales, lateral de registro administrativo y procedencia. En móvil se apila, envuelve nombres y pestañas, conserva RFC legible y controles táctiles. Navy/teal, Inter, Lucide y tokens existentes. No se añade biblioteca ni AppShell paralelo. El original del collage sigue ausente; la comparación es con la referencia escrita aprobada OQ-021, no un pixel-diff.

Resumen e Información y contactos están disponibles. Contactos explica su falta de habilitación; no afirma que el expediente carezca de contactos. Responsables y accesos, Certificados y alertas, Actividad e historial muestran navegación deshabilitada y texto accesible de no habilitación. No se presentan responsables supuestos, certificados, alertas o timeline sintéticos. Obligaciones, documentos y honorarios permanecen fuera de la ficha.

## Contratos consumidos, sin ampliación de autoridad

GET `/{tenantId}/taxpayers/{taxpayerId}` y GET `/{taxpayerId}/presentation` bajo `/api/v1/tenants`, ya implementados en #34/35. El cliente comprueba versión coincidente y DTO cerrado. Se muestran nombre/RFC/tipo/estado/nombre comercial, referencias MANUAL/PENDING_REVIEW, vigencias e incorporación/modificación reales. Sin verificación SAT, estadísticas ni consulta de journal.events.

Edición reutiliza TaxpayerForm: sólo nombre legal, nombre comercial y referencias; RFC/persona inmutables. PATCH usa expectedVersion. Archivo/restauración reutilizan TaxpayerLifecycle, motivos cerrados, confirmación, CSRF, Idempotency-Key, recuperación de resultado incierto y autorización fresca del servidor. Ningún comando actualiza optimistamente el estado. Sólo un recibo válido notifica éxito e invalida detalle/directorio/cuota. Un 409 conserva borrador y ofrece obtener datos actuales para revisar antes de guardar. Las cuotas, MFA y restricciones D74-03 conservan su aplicación backend.

La UI obtiene acciones de presentación por recurso; no decide por rol textual. Owner sin grant no accede por ser propietario. Contador/supervisor sólo ven y editan el expediente habilitado; auxiliar no accede. No hay API ni botones de asignar, conceder o recuperar permisos.

## Privacidad, errores y accesibilidad

Consulta por identidad/tenant/membresía/UUID, gcTime=0, sin persistencia, reintento automático o datos previos como placeholder. AbortSignal y desmontaje impiden aplicar respuestas tardías; onUnavailable retira datos y notificaciones antes de propagar 401/403 a la sesión. 404 muestra el mismo mensaje genérico de expediente no disponible. Errores recuperables muestran retry, sin conservar información obsoleta visible. UUID malformado no genera una consulta protegida. No se ponen nombre/RFC en título del navegador, almacenamiento, analítica o logs.

Pestañas de activación manual: flechas/Home/End mueven foco entre secciones disponibles, Enter/Espacio activa, aria-selected/controls/labelledby enlazan panel y pestaña. Botones y formularios compartidos conservan foco, Escape, etiquetas y errores; al cerrar edición se enfoca el encabezado o main estable. Se respeta reduced motion de #52. La evidencia recorre 1440×900/390×844 con axe y reflow 1280×800/768×1024/320; no equivale a certificación WCAG completa.

## Cobertura y límites

Vitest cubre datos, navegación interna/teclado, permisos de presentación, conflicto, error recuperable, 401/403/404, UUID inválido, aborto/respuesta tardía y entrada directa/cambio de despacho en el router real. Playwright OIDC añade propietario con perfil/edición/archivo/restauración/conflicto, contador y supervisor con grant exacto, auxiliar, suspensión/reactivación sin recuperar habilitación, cambio de rol, mismo RFC en dos despachos, respuesta real retenida y logout.

Los datos E2E provienen de backend/PostgreSQL/Keycloak reales y comandos autorizados. Sólo el fixture privado de tests añade una revisión de grant exacta para ejercer la matriz existente; no existe esa operación en producción. Un error de transporte se induce abortando la red, sin fabricar respuestas o datos. Se mantienen los recorridos previos de cuotas/MFA/idempotencia/seguridad. [Evidencia](evidence/issue-36/README.md) y [validación/criterios](../delivery/VALIDATION-36.md).

Contactos/certificados/responsabilidades/actividad y filtro de responsable requieren D36-01/02. No hay DDL nuevo: V13 sigue siendo última migración, V1–V13 intactas. Delegación/recuperación requiere además resolver D36-03 y su autoridad de bootstrap; una relación administrativa nunca equivale a acceso efectivo. No se afirma que la cartera o la ficha 360 completa estén terminadas.
