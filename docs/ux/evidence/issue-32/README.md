# Plan y uso — evidencia #32

Capturas reales de Chromium sobre el build de React, con viewports 1440×900 y 390×844. Los escenarios `ready`, `full`, `over`, `sin-consumo-medido`, `error`, `denied` y `cambio-despacho` usan respuestas sintéticas interceptadas únicamente por Playwright. **No son sesiones productivas ni evidencia de autorización backend.** OIDC/PostgreSQL/RLS se verifican separadamente en EntitlementTests y las suites #26–31.

Reproducir desde web: `FISCUVIA_CAPTURE=1 npx playwright test e2e/plan-usage.spec.ts --project=desktop --project=mobile`. El manifest registra los hashes de archivos fuente, del escenario y de cada PNG, motor y dimensiones reales. No se capturan proveedor, cookies, UUID visibles, contraseñas ni datos fiscales. Los fixtures no se empaquetan en el frontend.

Comparación contra DESIGN-SYSTEM-52 y la especificación escrita aprobada: se conservan navy/teal, Inter local, AppShell, PageHeader, controles de al menos 44px, StatCard, StatusPill y estados compartidos. Tarjetas con cuatro métricas en escritorio, dos en tamaño intermedio y una columna en móvil. Números grandes refluibles, estados con texto/icono además de color, sin precios ni botones comerciales. Los recursos futuros explican ausencia de medición; no se muestran como consumo cero.

Playwright comprueba axe A/AA, ausencia de overflow horizontal, rutas y estados, cache al cambiar despacho y retirada de datos en sesión expirada. Las suites anteriores conservan foco/teclado, menú móvil, reflow320 y zoom200%. Inspección visual de capturas desktop/móvil realizada; no es certificación WCAG ni pixel-diff #56. La columna móvil exige desplazamiento vertical para ver todas las métricas; se conserva legibilidad sin reducir texto.

La consulta productiva conserva DENY por defecto hasta aprobación de la política limitada. Estas capturas muestran presentación preparada y no acreditan un permiso instalado. Ver [fronteras de aprobación](../../../architecture/TENANT-ENTITLEMENTS.md).
