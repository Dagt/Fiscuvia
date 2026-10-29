# Portal del contribuyente — Future, propuesta sin aprobación MVP

**Actor:** contribuyente delegado; limitado a un contribuyente y a documentos explícitamente compartidos; puede tener acceso a varios si delegación validada individualmente.
## Navegación
Inicio -> Pendientes de documentación -> Documentos enviados -> Entregables compartidos -> Perfil/contacto.
## Flujo
Despacho crea solicitud -> cliente recibe notificación sólo con consentimiento -> abre portal -> sube documento -> queda 'Recibido / En validación' -> colaborador revisa -> cliente ve estado y puede consultar archivos compartidos.
## Seguridad
No mostrar otros contribuyentes del mismo despacho, información laboral interna, precios internos, credenciales SAT, cobros de otros; descargar sólo si `DOCUMENT_SHARE` autorizado. Sesión OIDC/portal, invitaciones expiran, MFA según riesgo.
## UX
Interfaz simplificada y mobile-first, textos sin jerga contable excesiva, fechas claras, acciones únicas por tarea, subida con barra de progreso, estados error y reintento. No fingir notificaciones WhatsApp si proveedor no integrado.
