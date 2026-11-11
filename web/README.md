# Fiscuvia web — design system y AppShell #52

## Configuración de despacho #31

`/app` consume nombres/estados propios de `/api/v1/me` y configura el despacho seleccionado mediante GET/POST `/api/v1/tenants/{tenantId}/settings`. Reutiliza el kit #52; sin operador inventado, alta pública ni datos DEMO en la vista operativa. Logo sin almacenamiento aprobado: referencia NULL y fallback Lucide. Formularios con versión/idempotencia/CSRF, confirmación, cancelar, carga/error y éxito sólo tras respuesta real. `canManage` es presentación, nunca seguridad; V7 instala la política de edición aprobada sólo para owner del propio despacho con MFA confiable reciente. Otros roles permanecen DENY. [Autoridad y configuración](../docs/architecture/TENANT-PROVISIONING.md).

Nuevo código en `areas/tenants` y cliente tipado `shared/api/firm-client.ts`. Queries incluyen usuario/tenant y AbortSignal; cambio, revocación y suspensión retiran contexto y datos anteriores. El harness local/test aprovisiona identidades y despachos sintéticos mediante el procedimiento real, fuera del JAR; la capacidad de edición procede exclusivamente de Flyway V7, igual que en una instalación normal. Ejecutar los comandos completos existentes y `node tools/ci/auth-browser.mjs` desde raíz; las pruebas OIDC verifican settings reales, no mocks de seguridad.

## Estado actual #52

[Referencia aprobada por Dagt](https://github.com/Dagt/Fiscuvia/issues/52#issuecomment-6598264416): opción B OQ-021, 2026-11-03. [Biblioteca implementada, props y tokens](../docs/ux/DESIGN-SYSTEM-52.md), [capturas](../docs/ux/evidence/issue-52/README.md). React19/TypeScript estricto/Vite/Tailwind, Inter local/Lucide/Radix/Query; ninguna dependencia nueva ni cambio de contratos backend.

`/login` conserva BFF/OIDC. `/app` comparte AppShell tras verificar identidad/membresías y selección por servidor; el contexto no es autorización reutilizable. Cache cancelada/retirada al cambiar despacho, respuestas tardías invalidadas, 401 expira sesión, logout POST CSRF. Sin localStorage/tokens ni datos fiscales. Nombres/logos comerciales esperan #31.

`/desarrollo`, `/desarrollo/estados` y **`/desarrollo/componentes`** son demostraciones públicas sin APIs. Catálogo P0/P1:125 filas sintéticas paginadas, nombres largos, controles/confirmación/paneles y estados DEMO. El filtro local no es un mecanismo de seguridad.

Estructura: `app/` routing/shell/providers; `areas/identity/` login/contexto; `areas/component-catalog/` composición DEMO; `shared/ui/` componentes; `shared/api/` transporte; `shared/presentation/` aviso; `styles/tokens.css` valores semánticos y `components.css` reglas compartidas. CSS anterior en capa legacy; sin kit paralelo.

Node24.13.1/npm11.8.0 y lockfile existentes. Desde `web`: `npm ci`, `npm run dev`, `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm test`, `npm run build`, `npm run test:e2e`. Desde raíz: `node tools/ci/auth-browser.mjs` (OIDC real), `node tools/ci/auth-browser.mjs --design-system-audit` (capturas), `node tools/ux/validate-design-system.mjs` (evidencia). Docker Linux/Ryuk/JDK25 para OIDC; [CI local/remota](../docs/delivery/CI.md).

Mínimos: **70 Vitest,33 Playwright fundación/catálogo y6 OIDC**, conservando todos los anteriores. Axe, cuatro viewports, reflow320/texto200%, teclado/foco y reduced-motion. Sin pixel-diff #56 ni certificación WCAG completa. Los bloques siguientes son antecedentes #21–30; sus contadores históricos no sustituyen estos mínimos.

## Cliente API #24 (para revisión)

Transporte headless tipado/validado para GET /api/v1/public/info; la UI no hace solicitudes nuevas ni cambia estilos. [Cliente y configuración](src/shared/api/README.md), [contrato implementado](../docs/api/openapi-v1.yaml). `VITE_API_BASE_URL` opcional, default /api/v1, ejemplo sin secretos; Vite dev proxy al backend local. El cliente público no usa cookies. El cliente de sesión #27 usa cookies same-origin y CSRF, sin tokens en localStorage ni autenticación ficticia. Preview necesita mismo origen/gateway o configuración CORS futura autorizada.

Tipos/metadatos se regeneran desde la raíz con `npm run generate:api --prefix tools/ci`; `npm run openapi --prefix tools/ci` exige coincidencia exacta. Web conserva sus 13 tests UI y añade 8 de cliente (**21 Vitest**), además de 13 Playwright. Sin cambios visuales: no se añaden capturas ni baselines #56.

**REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por presupuesto/facturación; seguimiento #79.** Presupuesto $0, pruebas locales completas, sin modificar la política de workflows.

Aplicación navegable de desarrollo con identidad Fiscuvia. No es un dashboard operativo: no contiene clientes, métricas fiscales, sesión ni autorización. El cliente público #24 es headless; las pantallas no consultan APIs de negocio.

## Instalación y comandos

Node 24.13.1 y npm 11.8.0 comprobados en Windows 11. `.nvmrc`, `engines`, `packageManager`, versiones exactas y `package-lock.json` fijan la base. `engine-strict` rechaza runtimes fuera de los rangos declarados. La primera instalación requiere acceso al registro npm; Inter se sirve localmente, sin Google Fonts ni CDN en ejecución.

Desde `web/`:

```powershell
npm ci
npm run dev
```

Abrir la URL local que imprime Vite. El servidor escucha en `127.0.0.1`. Para `/desarrollo` no se necesita Java, DB, Docker ni OIDC. `/login` y `/app` necesitan el BFF/IdP real de #27; sin ellos hay un estado de error/configuración, no acceso simulado. No hay variables de configuración obligatorias ni `.env` requerido. Nunca poner secretos/tokens en variables `VITE_`: se publican al navegador.

```powershell
npm run typecheck
npm run lint
npm run format:check
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

`test:e2e` compila y arranca automáticamente el build de producción en `127.0.0.1:4173`, y lo detiene al terminar. El puerto debe estar libre; no se reutiliza un servidor ajeno. `npm run preview` permite revisarlo manualmente después de `build`. `npm run test:watch` y `npm run format` son comandos de desarrollo.

Para regenerar sólo las capturas reales:

```powershell
npm run evidence
```

Las imágenes se escriben en [docs/ux/evidence/issue-21](../docs/ux/evidence/issue-21/README.md). No son snapshots aprobados ni un sistema de regresión visual terminado (#56). Los reportes HTML y trazas locales están en `playwright-report/` y `test-results/`, ignorados por Git.

## Versiones seleccionadas

| Tecnología                            | Versión                                                               |
| ------------------------------------- | --------------------------------------------------------------------- |
| React / React DOM                     | 19.3.1                                                                |
| TypeScript                            | 5.9.3, strict + noUncheckedIndexedAccess + exactOptionalPropertyTypes |
| Vite / plugin React                   | 8.3.5 / 6.1.2                                                         |
| Tailwind / plugin Vite                | 4.3.3 / 4.3.3                                                         |
| React Router                          | 8.5.1                                                                 |
| TanStack Query                        | 5.105.0                                                               |
| Radix Dialog / Slot                   | 1.3.1 / 1.5.0                                                         |
| Lucide React / Inter variable         | 1.64.0 / 5.3.0                                                        |
| Vitest / Testing Library React        | 5.0.5 / 16.3.3                                                        |
| ESLint / typescript-eslint / Prettier | 10.13.0 / 8.72.1 / 3.9.10                                             |
| Playwright / axe-core Playwright      | 1.64.1 / 4.14.0                                                       |
| jsdom                                 | 27.4.0                                                                |

Compatibilidad comprobada mediante instalación sin conflictos de peers, typecheck, tests y build. TypeScript 7 queda fuera del rango admitido por typescript-eslint 8.72.1; se elige 5.9.3 estable y compatible. jsdom 30 exige un patch de Node posterior al disponible; 27.4.0 admite Node 24.13.1. No se fuerza instalación con `--legacy-peer-deps`.

Se usa el patrón Button de shadcn/ui (Radix Slot + variantes), adaptado a Fiscuvia, y Radix Dialog para foco/modalidad del menú móvil. `components.json` prepara la ubicación de futuros componentes. shadcn es código local, no una dependencia runtime con versión propia; se conserva su licencia en `third-party/`. No se instala todo un kit sin consumidores. RHF/Zod se incorporarán cuando exista un formulario/contrato que los necesite; aquí no hay formularios.

## Arquitectura

```text
web/
├── public/favicon.svg
├── src/
│   ├── main.tsx
│   ├── app/                    # rutas, shell, navegación y composición de providers
│   ├── areas/
│   │   ├── welcome/            # bienvenida interna de desarrollo
│   │   └── interface-demo/     # estados explícitamente demostrativos
│   ├── shared/
│   │   ├── ui/                 # Button, Brand, PageHeader y estados
│   │   ├── presentation/       # notificación accesible, sin persistencia
│   │   ├── lib/                # composición de clases
│   │   └── api/README.md       # frontera y política para futuros contratos
│   ├── styles/                # tokens y composición responsive
│   └── test/
├── e2e/                       # smoke, accesibilidad y generación de evidencia
├── third-party/               # licencias de fuentes/patrones reutilizados
└── package.json, package-lock.json, configuraciones
```

Dirección de dependencias: `app -> areas/shared`, `areas -> shared`; shared no importa composición/áreas. ESLint impide esas dependencias inversas. No hay imports entre áreas ni dependencias circulares en la estructura actual. Los futuros dominios se añaden a `areas/` con sus contratos, consultas y rutas al implementar su issue, sin crear carpetas o servicios vacíos.

El QueryClient se crea por instancia del provider, sin cache global singleton. No hay queries ni solicitudes de negocio todavía. Los errores/reintentos futuros se definirán con el contrato correspondiente; la configuración inicial no reintenta automáticamente. Ver [frontera API](src/shared/api/README.md). La etiqueta “Sin despacho conectado” no selecciona ni autoriza un tenant y no hay identidad ficticia.

## Rutas y comportamiento

- `/` redirige a `/desarrollo`.
- `/desarrollo`: bienvenida, alcance real y acceso a la galería.
- `/desarrollo/estados?estado=vacio|carga|error|permisos`: demostraciones reutilizables, selección en URL y reintento local con notificación accesible. Un parámetro desconocido vuelve al ejemplo vacío.
- Cualquier ruta desconocida conserva el shell y ofrece volver al inicio. `/app/taxpayers` no está implementada; `/login` y `/app` son acceso y selección real de despacho en #27.

Contribuyentes, obligaciones, tareas, documentos, honorarios, reportes y CFDI aparecen deshabilitados bajo “Próximamente”. No son controles de autorización. El ejemplo “Sin permisos” es únicamente presentación; no implementa OIDC ni políticas backend.

El menú móvil usa un diálogo Radix con nombre/descripción, foco confinado, cierre con Escape y restitución de foco. Al navegar lleva el foco al contenido; la página incluye enlace de salto y títulos del documento en español. Controles principales de 44 px, foco visible y reducción de movimiento. Inter, iconos y estilos son locales. No se cargan datos en almacenamiento local ni cookies.

## Calidad y límites visuales

Revisión a 1440×900, 1280×800, 768×1024 y 390×844. Sidebar navy de 240 px en escritorio; menú modal por debajo de 1024; tarjetas apiladas en móvil. El contenido vertical se desplaza de forma normal, sin scroll horizontal artificial. También se comprueba 320 px y reflow con CSS zoom 200%; esto no equivale a probar todos los mecanismos de zoom del navegador.

La dirección autorizada viene de `DESIGN-SYSTEM.md`: navy #0C1D31, teal #087E80, lienzo #F5F8FB, tarjetas blancas, bordes #DCE5ED, radios 8–12 e Inter. El texto secundario se oscurece de #667A8F a #536B80 para contraste de tamaños pequeños, ajuste previsto por el propio documento. La marca vectorial es una composición tipográfica provisional de implementación, pendiente de revisión en #19/#52.

La imagen conceptual de seis pantallas no está en el repositorio. El propietario autorizó expresamente usar las especificaciones documentadas en esta issue; **no se afirma comparación visual exacta**. No se recrean las seis pantallas ni se muestran KPIs ficticios. La barra superior reserva el contexto sin simular búsqueda, alertas ni un usuario autenticado.

Metodología contrastada con `Dagt/TrailerSaas`: package/tooling, separación app/areas/shared, provider Query por instancia, pruebas de routing con MemoryRouter e interfaz ejecutándose. No se copian Firebase, lógica logística, rutas ni identidad Fleet Simple.

Resultados, capturas y revisión visual: [evidencia #21](../docs/ux/evidence/issue-21/README.md). Trazabilidad: ADR-004/011 como dirección, NFR-030..036 y NFR-040. Sus estados documentales no se cambian a Accepted.

## Pendientes independientes

#19: referencia original y revisión de marca. #52: kit completo, tokens definitivos, tablas/formularios/controles y consistencia en rutas de negocio. #56: baselines aprobados, regresión visual y auditoría ampliada de accesibilidad/navegadores. Esta entrega aporta fundamentos y evidencia, sin declarar resueltas esas issues. API de negocio, OIDC, permisos y datos fiscales quedan fuera.

## CI #23

`Web CI` define todos los comandos anteriores en Ubuntu 24.04 con Node de `.nvmrc` y npm 11.8.0. Instala Chromium con `npx --no-install playwright install --with-deps chromium`, respetando Playwright 1.64.1 del lockfile; conserva cinco proyectos sin reintentos ni exclusiones Draft. Desde #24 exige 21 Vitest y 13 Playwright, cero omitidos/fallidos/flaky. Cache de descargas npm, sin reutilizar node_modules/build. Ejecución remota pendiente #79.

Los JSON temporales se escriben en `../.ci-work/`; sólo se publica un resumen depurado. Capturas regeneradas, HTML, trazas y logs quedan locales al runner y no se suben automáticamente. Las capturas de #21 conservan su condición de evidencia histórica; #56 no se implementa aquí. Ver [guía CI](../docs/delivery/CI.md).

## Identidad y sesiones #27

[Guía completa](../docs/security/AUTHENTICATION.md). Desde la raíz, `node tools/ci/auth-browser.mjs` inicia PostgreSQL/Keycloak/BFF y Vite HTTPS efímeros, prueba Chromium real y limpia al terminar. `node tools/ci/auth-browser.mjs --serve` conserva el entorno para desarrollo hasta Ctrl+C. Sólo usuarios sintéticos de fixtures; credenciales privadas en `.ci-work/27-browser.json`, nunca artifacts ni VITE_. El BFF usa 127.0.0.1; Keycloak localhost, sin compartir dominio de cookies.

`session-client.ts` restringe base a `/api/v1` same-origin, `credentials: same-origin`, no-store y CSRF obtenido antes de cada mutación. Login es un formulario POST que redirige al IdP; no se usa fetch para seguir OAuth. La sesión, identidad y roles vienen del backend. Seleccionar un despacho exige respuesta autorizada, no se guarda tenant en localStorage. Logout invalida sesión real; 401 conduce a sesión expirada. Los nombres de despacho aún no existen en el modelo mínimo: la UI muestra un ordinal de las membresías reales, sin inventar nombres fiscales.

37 Vitest (21 anteriores + 16 nuevos) y 13 Playwright de fundación más 5 OIDC reales son los mínimos. [Capturas #27](../docs/ux/evidence/issue-27/README.md) y [resultados](../backend/VALIDATION-27.md). No hay proveedor de producción aprobado ni funcionalidades fiscales operativas.

## Plan y uso #32

FirmWorkspace permite elegir Datos del despacho o Plan y uso dentro de la selección autorizada. La consulta usa claves actor/tenant y AbortSignal, sin persistencia de selección ni tokens. StatCard/StatusPill y estados #52 distinguen ausencia de configuración, medición, reserva, disponible, sobrecupo y recursos no integrados. Sin precios ni botones comerciales; TENANT_PLAN_VIEW permanece denegado sin aprobación explícita. Fixtures de presentación sólo en pruebas; [evidencia](../docs/ux/evidence/issue-32/README.md), [modelo y pendientes](../docs/architecture/TENANT-ENTITLEMENTS.md).
