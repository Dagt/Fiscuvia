# Evidencia real #33: invitaciones de correo local/test

Alcance A aprobado y correo B local/test: [flujo y controles](../../../security/LOCAL-EMAIL-INVITATIONS-33.md). El directorio, reactivación y cambios operativos de rol ya tienen aprobación limitada del propietario y se implementan en V11; cartera conserva #74. No existe entrega externa de correo ni cartera simulada. [Administración y controles](../../../security/TEAM-ADMINISTRATION-33.md).

## Reproducción y procedencia

Confirmar primero la fuente en Git. Ejecutar `FISCUVIA_TEAM_CAPTURE=1 node tools/ci/auth-browser.mjs` (PowerShell: `$env:FISCUVIA_TEAM_CAPTURE='1'`; quitar la variable al terminar). El harness utiliza PostgreSQL18.6, Keycloak26.8.1 y Chromium reales, identidades y despachos DEMO. No captura la pantalla del proveedor, contraseñas, OTP ni el token. El estado posterior a crear se captura después de retirarlo explícitamente. El resultado procede de HTTP201; la aceptación y revocación se comprueban contra la API real con la identidad destinataria.

`capture-manifest.json` identifica SHA fuente, trees de aplicación/backend/fixtures, versión Chromium, dimensiones y SHA256 de cada PNG. El commit posterior sólo agrega evidencia documental; los trees permiten comprobar que las capturas corresponden al código del HEAD final sin autorreferencia del SHA. La validación final y CI se repiten sobre ese HEAD. Las capturas no acreditan el alcance pendiente del issue.

## Estados y verificación

Cinco estados, cada uno a1440×900 y390×844: `cargando`, `formulario`, `creada-entrega-manual`, `sin-permiso` y `error-destinatario`. Los nombres exactos y hashes constan en el manifiesto. Axe WCAG2/2.1/2.2 AA, ausencia de overflow horizontal y de material privado se verifican antes de cada captura. El escenario retiene temporalmente una respuesta real ya autorizada sólo para observar la carga; no inventa respuestas de negocio ni autorización.

## Comparación con la referencia aprobada

Se conservan AppShell navy, acentos teal, Inter local, Field, Button, StatusPill, ConfirmationDialog y estados del sistema #52. Se compone la pestaña Equipo dentro de la configuración real; no se crea otro tema. La administración actual incorpora directorio e invitaciones reales y panel lateral de acciones; la cartera futura no se representa como operativa. El panel explica entrega manual y cupo al aceptar, sin indicar correo enviado. En móvil el contenido y formulario se apilan; los nombres y mensajes pueden ocupar varias líneas. Esta comparación usa la especificación escrita autorizada, no una comparación pixel a pixel con el collage original ausente.

La revisión visual humana y el resultado final de las ejecuciones se resumen en el PR Draft. No publicar traces, handoff, logs completos ni respuestas con el token como artifacts.

## Inventario comprobado

| Estado real | Escritorio 1440×900 | Móvil 390×844 |
|---|---|---|
| Carga autorizada | [Captura](cargando-1440.png) | [Captura](cargando-390.png) |
| Formulario | [Captura](formulario-1440.png) | [Captura](formulario-390.png) |
| Creación confirmada, token retirado | [Captura](creada-entrega-manual-1440.png) | [Captura](creada-entrega-manual-390.png) |
| Acceso denegado por backend | [Captura](sin-permiso-1440.png) | [Captura](sin-permiso-390.png) |
| Destinatario inelegible, HTTP422 real | [Captura](error-destinatario-1440.png) | [Captura](error-destinatario-390.png) |

Revisión visual: jerarquía legible, campos y acciones alineados, mensajes ES-MX y componentes #52 conservados; la vista móvil usa scroll vertical normal para alcanzar las acciones. No hay desbordamiento horizontal. Se comprobaron dimensiones PNG reales y SHA256 de las diez imágenes, además de los tres trees de fuente del manifiesto. Axe reportó cero violaciones en los diez estados/tamaños; esto no se presenta como certificación completa de WCAG.

## Ampliación A: evidencias adicionales

`equipo-colaboradores`, `equipo-cambio-rol`, `equipo-confirmacion`, `equipo-suspendido`, `equipo-revocado`, `equipo-otro-despacho`, `equipo-sin-cupo`, `equipo-invitaciones`, `equipo-vacio` y `equipo-error-recuperable`, en1440×900 y390×844. El manifiesto de la ejecución final determina los archivos efectivamente capturados; no acreditar una imagen pendiente antes de generarla. Se conservan los10 estados/tamaños B, recapturados con la navegación actual.

Las mutaciones y el error de cuota son HTTP reales con PostgreSQL/Keycloak; el error recuperable de transporte se induce abortando una única solicitud del navegador y se reintenta luego contra el backend real. No se simula autorización ni éxito. El rol actualizado se comprueba también en /me del destinatario; suspensión/revocación/revocación de invitación se comprueban mediante HTTP denegado al receptor. Cambio de despacho elimina contactos anteriores y un UUID conocido del otro despacho devuelve404.

El escenario administrativo revisa además1280,768 y320 CSS px mediante axe y overflow. Panel y confirmaciones mantienen estado al cambiar de ancho; el foco vuelve a Colaboradores al cerrar. El concepto escrito #52 es la referencia; no se afirma pixel-diff contra una imagen ausente ni certificación completa de WCAG.
