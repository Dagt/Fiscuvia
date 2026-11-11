# Evidencia real #33: invitaciones de correo local/test

Alcance parcial aprobado: [flujo y controles](../../../security/LOCAL-EMAIL-INVITATIONS-33.md). Directorio general, reactivación y cambio de roles permanecen pendientes de aprobación; cartera conserva #74. No existe entrega externa de correo ni una pantalla simulada de equipo.

## Reproducción y procedencia

Confirmar primero la fuente en Git. Ejecutar `FISCUVIA_TEAM_CAPTURE=1 node tools/ci/auth-browser.mjs` (PowerShell: `$env:FISCUVIA_TEAM_CAPTURE='1'`; quitar la variable al terminar). El harness utiliza PostgreSQL18.6, Keycloak26.8.1 y Chromium reales, identidades y despachos DEMO. No captura la pantalla del proveedor, contraseñas, OTP ni el token. El estado posterior a crear se captura después de retirarlo explícitamente. El resultado procede de HTTP201; la aceptación y revocación se comprueban contra la API real con la identidad destinataria.

`capture-manifest.json` identifica SHA fuente, trees de aplicación/backend/fixtures, versión Chromium, dimensiones y SHA256 de cada PNG. El commit posterior sólo agrega evidencia documental; los trees permiten comprobar que las capturas corresponden al código del HEAD final sin autorreferencia del SHA. La validación final y CI se repiten sobre ese HEAD. Las capturas no acreditan el alcance pendiente del issue.

## Estados y verificación

Cinco estados, cada uno a1440×900 y390×844: `cargando`, `formulario`, `creada-entrega-manual`, `sin-permiso` y `error-destinatario`. Los nombres exactos y hashes constan en el manifiesto. Axe WCAG2/2.1/2.2 AA, ausencia de overflow horizontal y de material privado se verifican antes de cada captura. El escenario retiene temporalmente una respuesta real ya autorizada sólo para observar la carga; no inventa respuestas de negocio ni autorización.

## Comparación con la referencia aprobada

Se conservan AppShell navy, acentos teal, Inter local, Field, Button, StatusPill, ConfirmationDialog y estados del sistema #52. Se compone la pestaña Equipo dentro de la configuración real; no se crea otro tema. La diferencia de alcance con el concepto futuro es explícita: no hay directorio, cartera ni controles para reactivar o reasignar roles. El panel explica entrega manual y cupo al aceptar, sin indicar correo enviado. En móvil el contenido y formulario se apilan; los nombres y mensajes pueden ocupar varias líneas. Esta comparación usa la especificación escrita autorizada, no una comparación pixel a pixel con el collage original ausente.

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
