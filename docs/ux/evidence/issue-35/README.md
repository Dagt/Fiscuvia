# Evidencia real C02 #35

Fuente ejecutada: `2abe478b1a1603631360bbb5ada02bbe3f776226`. Navegador Chromium `156.0.8078.4`, capturado 2026-11-13T19:21:47.089Z. [Manifiesto con SHA-256 y árboles de código](capture-manifest.json). 42 imágenes, 21 escenarios en 1440×900 y 390×844. No son mockups ni imágenes generadas. El commit de entrega añade documentación/evidencia; el árbol web/backend/fixtures debe conservar los hashes registrados.

## Procedencia y resultados

Aplicación React real sobre backend Spring Boot, PostgreSQL 18/Flyway V1–V13 y Keycloak con contraseña/OTP efímeros. Datos exclusivamente sintéticos: RFC DEM, nombres DEMO, correos example.invalid. Altas, modificaciones, cuota, archivo/restauración e invitaciones pasan por APIs reales; la revisión explícita de grants y la expiración MFA sólo usan fixtures privados fuera del JAR. No se sustituyen éxitos, perfiles o permisos mediante mocks. Error de transporte y respuesta perdida se provocan abortando la red; esta última se aborta después de ejecutar el comando real y se recupera con la misma clave.

Resultado esperado: datos sólo del despacho/expedientes autorizados, comandos confirmados, errores sin éxito ficticio, controles legibles y sin desbordamiento. Resultado observado: los 24 recorridos OIDC pasan sin retries ni omisiones, incluidos los cuatro nuevos de contribuyentes. Cada captura pasa axe con etiquetas WCAG 2 A/AA, 2.1 AA y 2.2 AA y comprobación de overflow. También se verifican 1280×800, 768×1024 y 320×844 en estos estados. Se espera a las transiciones CSS finitas antes de medir contraste, sin bajar umbrales. Las herramientas no certifican cumplimiento WCAG completo.

## Escenarios

| Escenario | Escritorio 1440×900 | Móvil 390×844 | Observado |
|---|---|---|---|
| Vacío | [Ver](01-vacio-1440.png) | [Ver](01-vacio-390.png) | Estado real esperado, axe 0 y sin overflow |
| Alta | [Ver](02-alta-1440.png) | [Ver](02-alta-390.png) | Estado real esperado, axe 0 y sin overflow |
| Formulario inválido | [Ver](03-formulario-invalido-1440.png) | [Ver](03-formulario-invalido-390.png) | Estado real esperado, axe 0 y sin overflow |
| Registro confirmado | [Ver](04-registro-confirmado-1440.png) | [Ver](04-registro-confirmado-390.png) | Estado real esperado, axe 0 y sin overflow |
| Información administrativa | [Ver](05-informacion-administrativa-1440.png) | [Ver](05-informacion-administrativa-390.png) | Estado real esperado, axe 0 y sin overflow |
| Edición | [Ver](06-edicion-1440.png) | [Ver](06-edicion-390.png) | Estado real esperado, axe 0 y sin overflow |
| Conflicto de versión | [Ver](07-conflicto-version-1440.png) | [Ver](07-conflicto-version-390.png) | Estado real esperado, axe 0 y sin overflow |
| Directorio con datos y nombre largo | [Ver](08-directorio-1440.png) | [Ver](08-directorio-390.png) | Estado real esperado, axe 0 y sin overflow |
| Búsqueda RFC | [Ver](09-busqueda-rfc-1440.png) | [Ver](09-busqueda-rfc-390.png) | Estado real esperado, axe 0 y sin overflow |
| Filtros | [Ver](10-filtros-1440.png) | [Ver](10-filtros-390.png) | Estado real esperado, axe 0 y sin overflow |
| Sin coincidencias | [Ver](11-sin-coincidencias-1440.png) | [Ver](11-sin-coincidencias-390.png) | Estado real esperado, axe 0 y sin overflow |
| Confirmación de archivado | [Ver](12-confirmacion-archivado-1440.png) | [Ver](12-confirmacion-archivado-390.png) | Estado real esperado, axe 0 y sin overflow |
| Archivados | [Ver](13-archivados-1440.png) | [Ver](13-archivados-390.png) | Estado real esperado, axe 0 y sin overflow |
| Restauración confirmada | [Ver](14-restauracion-confirmada-1440.png) | [Ver](14-restauracion-confirmada-390.png) | Estado real esperado, axe 0 y sin overflow |
| Cuota agotada | [Ver](15-cuota-agotada-1440.png) | [Ver](15-cuota-agotada-390.png) | Estado real esperado, axe 0 y sin overflow |
| Restauración rechazada | [Ver](16-restauracion-rechazada-1440.png) | [Ver](16-restauracion-rechazada-390.png) | Estado real esperado, axe 0 y sin overflow |
| RFC duplicado | [Ver](17-rfc-duplicado-1440.png) | [Ver](17-rfc-duplicado-390.png) | Estado real esperado, axe 0 y sin overflow |
| MFA insuficiente | [Ver](18-mfa-insuficiente-1440.png) | [Ver](18-mfa-insuficiente-390.png) | Estado real esperado, axe 0 y sin overflow |
| Respuesta perdida/recuperación | [Ver](19-respuesta-perdida-1440.png) | [Ver](19-respuesta-perdida-390.png) | Estado real esperado, axe 0 y sin overflow |
| Error recuperable | [Ver](20-error-recuperable-1440.png) | [Ver](20-error-recuperable-390.png) | Estado real esperado, axe 0 y sin overflow |
| Sin permiso real | [Ver](21-sin-permiso-1440.png) | [Ver](21-sin-permiso-390.png) | Estado real esperado, axe 0 y sin overflow |

## Comparación visual

Referencia: [Clientes S03](../../reference-library/SCREENS-BASELINE.md), [especificación escrita aprobada](../../reference-library/VISUAL-SPEC.md) y [componentes #52](../../reference-library/COMPONENTS-52.md). El collage original sigue ausente: no se afirma comparación pixel a pixel.

Se conservan sidebar navy de 240 px, acciones teal, Inter local, tokens de espaciado/bordes/radios, controles táctiles y shell existentes. Encabezado y acción prioritaria, una superficie de directorio, tabla en escritorio y tarjetas legibles en móvil, filtros plegables, panel administrativo y confirmaciones compartidas. El título largo del topbar se trunca; el H1 permanece completo. Los nombres largos envuelven; RFC y estados se mantienen legibles. Los formularios extensos desplazan su panel, no toda la página.

Diferencias justificadas del concepto: se denomina Contribuyentes; no hay KPIs/total global, responsable supuesto, catálogo SAT o ficha 360°. Se muestran sólo los campos/autorizaciones del contrato. Responsable requiere el contrato de lectura de #36. Revisión por agente: marca, jerarquía, legibilidad, densidad, responsive, accesibilidad, estados y facilidad de tarea ≥4/5 conforme a la rúbrica del repositorio; queda pendiente la aceptación humana del PR.

## Verificación reproducible

Desde raíz: `node tools/ux/validate-taxpayer-directory.mjs --current` comprueba 42 PNG, dimensiones, SHA-256, escenarios y correspondencia con los árboles actuales. `Get-FileHash docs/ux/evidence/issue-35/08-directorio-1440.png -Algorithm SHA256` permite contrastar un archivo con el manifiesto. Para reproducir las capturas con recursos locales efímeros: `$env:FISCUVIA_TAXPAYER_CAPTURE='1'; node tools/ci/auth-browser.mjs`, sobre el código confirmado.
