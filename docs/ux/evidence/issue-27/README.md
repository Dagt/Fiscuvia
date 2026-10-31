# Acceso e identidad #27 — capturas reales

Capturas PNG obtenidas con Chromium/Playwright de React ejecutándose en HTTPS con BFF Spring Boot, PostgreSQL y Keycloak reales. No son imágenes conceptuales ni de IA. Comando reproducible desde raíz: `node tools/ci/auth-browser.mjs`; configuración `web/playwright.auth.config.ts`, pruebas `web/auth-e2e/session.spec.ts`. SHA final y resultados completos: PR #83 y [validación](../../../../backend/VALIDATION-27.md).

| Estado | Escritorio 1440×900 | Móvil 390×844 |
|---|---|---|
| Acceso con servicio OIDC disponible | [login](login-1440.png) | [login](login-390.png) |
| Fallo real de autenticación por state alterado | [error](error-1440.png) | [error](error-390.png) |
| Identidad verificada sin membresías | [sin membresías](sin-membresias-1440.png) | Cubierto por layout responsive y tests |
| Despacho autorizado por membresía real | [autorizado](despacho-autorizado-1440.png) | Cubierto por layout responsive y tests |

La captura de error proviene de un recorrido real: inicio POST+CSRF, URL de autorización modificada, contraseña/OTP reales de un fixture y rechazo del callback. No se sustituye el backend por respuestas simuladas. Las capturas se toman únicamente en Fiscuvia, después del redirect seguro; no muestran el IdP con credenciales, tokens, UUID, RFC, datos fiscales ni usuarios reales.

Comparación con la dirección documentada: navy #0C1D31, teal #087E80, Inter y marca existentes, fondos claros, jerarquía sobria, bordes suaves y controles/foco visibles. El acceso usa un panel de marca en escritorio y cabecera compacta en móvil; la sesión muestra únicamente membresías verificadas y disponibilidad futura de áreas fiscales. No añade métricas ni dashboard aparente. No cambia la base visual `/desarrollo` ni adopta Fleet Simple.

Axe revisa acceso y error en ambos tamaños; se comprueban teclado/foco y ausencia de overflow. La imagen conceptual original de seis pantallas no está en el repositorio: esta comparación utiliza las especificaciones escritas autorizadas y **no afirma una comparación visual exacta**. #52/#56 conservan su alcance, sin declararse completados.
