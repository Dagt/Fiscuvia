# C03 #36 — decisiones de autorización para revisión

Estado: **Proposed; no concede permisos**. Base main `05f1d0b58783438e193ae9ad86e7181b0e62c135`, merge humano #94. D74-01..05 permanecen aceptadas en su alcance original. La petición C03 autoriza construir la ficha y diseñar estas extensiones, pero advierte expresamente que no se habiliten nuevas escrituras/delegación por suposición.

## D36-01 — alcance de lectura de la ficha

Propuesta: TAXPAYER_VIEW efectivo sobre el UUID exacto permite consultar, además del perfil actual:

- Contactos administrativos del propio expediente: nombre, relación, correo/teléfono capturados, principal, estado, versión y fechas. No son identidades OIDC ni prueba de representación legal.
- Metadatos manuales EFIRMA/CSD: tipo, vencimiento conocido/desconocido, procedencia MANUAL/PENDING_REVIEW, estado administrativo, versión y captura. Sin archivos, seriales criptográficos, claves, contraseñas o consultas SAT.
- Responsabilidades del propio expediente: relación PRIMARY real y sus fechas, UUID de membresía del mismo tenant, nombre actual cuando exista, rol operativo y estado de membresía. **Sin email/teléfono de colaboradores, userId global, issuer/subject, sesiones ni listado global de identidades.** No se infiere efectividad de acceso a partir de la relación.
- Actividad administrativa mínima por contribuyente: código cerrado de operación, fecha y versión; sin snapshots antiguos de PII, actores identificados ni consulta directa a journal.events. No concede AUDIT_VIEW.
- Opciones/filtro de responsable limitados a PRIMARY actuales de expedientes que el solicitante puede consultar; RLS antes de opciones, filtros y paginación. No devuelve una plantilla completa del despacho ni conteos de cartera ajena.

Se conservan los tres roles con VIEW ya autorizados (owner/supervisor/contador), identidad/membresía/tenant ACTIVE y última revisión efectiva del grant exacto ligada a versión/rol. Auxiliar DENY. No instala capacidades o grants de expedientes. La implementación usa proyecciones SQL estrechas con contexto exacto; la autorización se comprueba nuevamente en cada GET. Sin aprobación, se mantiene exclusivamente la lectura del perfil C01 y se identifican estas secciones como pendientes de autorización.

## D36-02 — contactos y metadatos de certificados

Propuesta: ampliar **expresamente**, sólo para #36, el alcance de TAXPAYER_EDIT efectivo sobre el contribuyente ACTIVE para crear/editar/desactivar contactos y metadatos administrativos EFIRMA/CSD, y designar contacto principal. Misma matriz owner/supervisor/contador con grant/revisión vigente; auxiliar DENY. No añade una obligación MFA a estas escrituras administrativas ordinarias: se conserva la regla de EDIT de D74-03. MFA sigue siendo obligatorio para archivo/restauración y cualquier futura administración de accesos.

Cada escritura requiere CSRF, comando idempotente, versión administrativa esperada y versión del hijo cuando exista, recurso/tenant exactos, validación cerrada y auditoría/outbox/recibo atómicos. Datos/actor/fechas/versiones de servidor. No DML directo runtime ni modificación de identidad/contacto OIDC. No otorga ni revalida grants. Sin aprobación **no se instala su DDL ni sus endpoints operativos**.

Diseño acotado propuesto:

- Contacto: UUID, tenant/contribuyente, nombre 1–160, relación opcional hasta120, correo opcional hasta254 y teléfono opcional hasta40, principal, ACTIVE/INACTIVE y versión/fechas. Hasta20 activos por expediente; correo no único ni identificador legal. Como máximo un principal ACTIVE. Principal se elige explícitamente; desactivarlo no elige otro por suposición. Sin reactivación automática ni DELETE. Inactivos conservan trazabilidad.
- Certificado: UUID y tenant/contribuyente, EFIRMA/CSD, expiresOn opcional, MANUAL/PENDING_REVIEW, ACTIVE/INACTIVE, versión y actores/fechas privados. Como máximo un registro administrativo ACTIVE por tipo; renovaciones conservan los anteriores inactivos. No carga de .key/.cer ni campos libres para secretos.
- Alertas sólo administrativas: fecha desconocida, fecha pasada, vence hoy/en hasta30 días, o fecha futura sin alerta. Fecha civil del servidor en America/Mexico_City; 30 días fijos, documentados y sin configuración ni carácter legal. Registro INACTIVE no afirma cancelación oficial y no genera alerta vigente. Sin jobs/notificaciones automáticas.
- Un pequeño agregado administrativo versionado por expediente serializa cambios de principal/contactos/certificados. Evita sobreescribir una designación concurrente aunque ambos contactos tengan versiones individuales todavía actuales. Historial append-only y recibos privados, FK compuestas y FORCE RLS. No modifica el versionado del perfil C01 ni anticipa obligaciones/documentos.

Alternativa si se difiere D36-02: entregar la ficha y las lecturas aprobadas de D36-01; los controles de nuevas escrituras no aparecen. El diseño queda separado como propuesta, sin tablas vacías de funcionalidades bloqueadas ni éxitos simulados.

## D36-03 — delegación, responsabilidades y recuperación: bloqueadas

No se solicita aprobarlas implícitamente junto a D36-01/02. El mensaje C03 declara su política como propuesta; D74-03 reservó ASSIGN sin habilitación operativa. Se requieren decisiones separadas y revisión de diseño sobre:

1. **Quién delega:** propuesta owner únicamente, ACTIVE y MFA confiable ≤10min, TAXPAYER_ASSIGN exacto efectivo y revisión vigente. No supervisor/contador por jerarquía.
2. **Arranque de ASSIGN:** sus grants iniciales carecen de revisión habilitante. No habilitarlos masivamente mediante migración ni un endpoint autoconcesivo. Definir autoridad confiable y aprobación expresa que pueda habilitar el primer administrador del expediente.
3. **Pérdida de acceso del owner:** un cambio de rol o suspensión/reactivación invalida revisiones históricas. Definir recuperación fuera del grant obsoleto, verificación del propietario, actor confiable y evidencia de revisión. No usar posesión del tenant/RFC o del grant histórico como autoridad.
4. **Destinatarios/capacidades:** mismo tenant, ACTIVE, rol único actual, VIEW/EDIT explícitos para owner/supervisor/contador; auxiliar DENY. No delegar ASSIGN/ARCHIVE por arrastre, copiar todos los grants del actor ni dar cartera global.
5. **Rehabilitación:** comando específico para revisión del grant/versiones/rol actuales, separado de reactivar colaborador. Nunca buscar una revisión ENABLE antigua ni revivirla automáticamente.
6. **Responsabilidad:** asignación administrativa independiente del acceso; PRIMARY exactamente uno, CONTRIBUTOR explícitos, sustitución/fin sin backdating y con historial. Estado suspendido/revocado conserva autoría y relación hasta cambio explícito. No reemplazar automáticamente al principal.
7. **Concurrencia/auditoría:** versiones de expediente/relación/grant y membresía, locks compatibles con #33–35, actor/MFA frescos, idempotencia, motivos cerrados, journal/outbox/recibo atómicos. Proteger la última autoridad administradora mediante una regla expresamente aprobada.

Mientras no se resuelvan estos puntos no hay filas operativas TAXPAYER_ASSIGN, endpoints de asignación/revisión, recuperación, cambios de grants ni DDL de responsabilidades adicionales. La ficha distinguirá responsabilidad, rol, grant, revisión efectiva y permiso final; no declarará cartera operativa completada. #37/#38 permanecen fuera de alcance.
