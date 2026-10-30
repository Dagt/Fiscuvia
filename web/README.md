# Fiscuvia web — scaffold #21

Aplicación navegable de desarrollo con identidad Fiscuvia. No es un dashboard operativo: no contiene clientes, métricas fiscales, sesión, autorización ni conexión API. El backend de #20 permanece intacto.

## Instalación y comandos

Node 24.13.1 y npm 11.8.0 comprobados en Windows 11. `.nvmrc`, `engines`, `packageManager`, versiones exactas y `package-lock.json` fijan la base. `engine-strict` rechaza runtimes fuera de los rangos declarados. La primera instalación requiere acceso al registro npm; Inter se sirve localmente, sin Google Fonts ni CDN en ejecución.

Desde `web/`:

```powershell
npm ci
npm run dev
```

Abrir la URL local que imprime Vite. El servidor escucha en `127.0.0.1`. No se necesita arrancar Java, DB, Docker ni OIDC. No hay variables de configuración obligatorias ni `.env` requerido. Nunca poner secretos/tokens en variables `VITE_`: se publican al navegador.

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
- Cualquier ruta desconocida conserva el shell y ofrece volver al inicio. `/app/taxpayers` y `/login` no están implementadas.

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

`Web CI` ejecuta todos los comandos anteriores en Ubuntu 24.04 con Node de `.nvmrc` y npm 11.8.0. Instala Chromium con `npx --no-install playwright install --with-deps chromium`, respetando Playwright 1.64.1 del lockfile; ejecuta los cinco proyectos, sin reintentos ni exclusiones Draft. Exige al menos 13 Vitest y 13 Playwright, cero omitidos/fallidos/flaky. Se cachean descargas npm, sin reutilizar node_modules/build.

Los JSON temporales se escriben en `../.ci-work/`; sólo se publica un resumen depurado. Capturas regeneradas, HTML, trazas y logs quedan locales al runner y no se suben automáticamente. Las capturas de #21 conservan su condición de evidencia histórica; #56 no se implementa aquí. Ver [guía CI](../docs/delivery/CI.md).
