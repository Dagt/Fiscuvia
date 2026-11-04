# Evidencia real de despachos — #31

Chromium **156.0.8078.4**, aplicación React/BFF ejecutándose en HTTPS loopback, PostgreSQL18.6 y Keycloak26.8.1 reales. Fuente de aplicación capturada: **88f9ea34cc5a91ab96c492f6493a39c489e12dbd**. [Manifest con hashes, dimensiones, árboles de fuente y controles](capture-manifest.json). El validador compara UI/backend y fixtures utilizados con el HEAD final; las capturas no se reetiquetan como realizadas en otro commit. Las aserciones de tests/documentación posteriores no alteran los componentes capturados.

**La política positiva de edición es un fixture explícito exclusivamente local/test. Su grant permanente sigue DENY y requiere aprobación expresa del propietario.** No son datos de clientes ni una cuenta de operador SaaS; las identidades OIDC se verifican primero y el procedimiento administrativo real crea los despachos/membresías. Los nombres llevan DEMO y el contacto usa example.invalid. No se captura el IdP, contraseña, OTP, tokens, UUID internos ni consultas privadas. No se utilizan imágenes de IA.

## Inventario

| Estado / evidencia | Escritorio | Móvil |
|---|---|---|
| Formulario y nombre real en shell | [1440×900](formulario-1440.png), [1280×800](formulario-1280.png), [768×1024](formulario-768.png) | [390×844](formulario-390.png) |
| Parte inferior y controles al desplazar | [1440](formulario-acciones-1440.png) | [390](formulario-acciones-390.png) |
| Selector, dos despachos propios persistidos | [1440](selector-1440.png) | [390](selector-390.png) |
| Fallback sin logo | [1440](sin-logo-1440.png) | [390](sin-logo-390.png) |
| Nombre largo autorizado | [1440](nombre-largo-1440.png) | [390](nombre-largo-390.png) |
| Validación requerida asociada al campo | [1440](validacion-1440.png) | [390](validacion-390.png) |
| Confirmación Radix, antes de mutar | [1440](confirmacion-1440.png) | [390](confirmacion-390.png) |
| Guardado POST real + GET persistido + nombre actualizado | [1440](guardado-1440.png) | [390](guardado-390.png) |
| Loading sin datos previos | [1440](loading-1440.png) | [390](loading-390.png) |
| Error recuperable, reintento real | [1440](error-1440.png) | [390](error-390.png) |
| Cuenta ACCOUNTANT, lectura sin edición | [1440](solo-lectura-1440.png) | [390](solo-lectura-390.png) |
| Revocación real durante carga, 403 y contexto retirado | [1440](denegado-1440.png) | [390](denegado-390.png) |
| Suspensión por procedimiento administrativo real | [1440](suspendido-1440.png) | [390](suspendido-390.png) |

Son **28 PNG** de viewport, no imágenes completas reducidas. El formulario móvil requiere desplazamiento vertical; la segunda toma muestra todos los campos inferiores y guardar/cancelar. Error de red y loading se inducen mediante bloqueo/aborto del transporte en Chromium, nunca respondiendo éxito inventado. Denegación y suspensión provienen del backend real; no de un 403 fabricado en el navegador. La aceptación de invitación y revocación usan sus APIs reales; la administración completa #33 no se incorpora.

## Comparación y accesibilidad

Referencia autorizada: [biblioteca #52](../../DESIGN-SYSTEM-52.md) y especificación escrita aprobada de #19. Se conservan navy/teal, Inter local, sidebar240, header y espaciados del kit. Cambios concretos: nombres ordinales sustituidos por nombres backend, encabezado corto móvil, configuración con Field, dos columnas escritorio/una móvil, preview de marca sin almacenamiento de logo, acciones en PageHeader, confirmación y notificación compartidas. Las zonas tienen etiquetas ES-MX. No hay dashboard fiscal, KPIs, contribuyentes ni alertas inventados. El collage original permanece ausente; no se afirma comparación exacta de píxeles ni se incorpora #56.

Cada captura verifica PNG/hash/dimensiones, axe A/AA aplicable y ausencia de overflow global; seis controles adicionales verifican reflow320 CSS px, CSS zoom200%, Escape/restauración de foco, reintento, revocación y suspensión. Revisión visual manual: jerarquía, nombres extensos, fallback, foco perceptible y controles utilizables al desplazar. Playwright verifica teclado, foco inicial de cancelar y contextos autenticados. No equivale a certificación WCAG completa, prueba de todos los lectores de pantalla o aprobación automática de diseño/permisos. Contraste se mantiene mediante los tokens aprobados; no hay nueva paleta libre.

## Reproducción y límites

Con árbol de aplicación y harness confirmado en Git, Docker, Java25 y Node24:

```sh
node tools/ci/auth-browser.mjs --tenant-provisioning-audit
node tools/ux/validate-tenant-provisioning.mjs
```

No ejecutar simultáneamente con Maven clean o con otro harness que ocupe4174. El runner crea y elimina sus contenedores; claves/config/logs quedan privados en .ci-work y no se publican como artifacts. Reejecutar genera UUID/credenciales/correlaciones diferentes, sin cambiar el protocolo. La captura de un nombre no autoriza su consulta: cada petición usa membresía/capacidad/estado y RLS de nuevo.

Pendientes: aprobación del grant owner+MFA; proveedor/MFA productivo OQ-014; matriz completa OQ-015; baja/retención OQ-016; almacenamiento privado de logos; planes #32/equipo #33; gate fiscal #74. La UI retira datos al recibir denegación o revalidar identidad; no existe un canal push que anuncie revocaciones a una pestaña inactiva. El backend rechaza cada operación nueva después de revocación/suspensión, independientemente del estado visual.
