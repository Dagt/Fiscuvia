# Biblioteca implementada #52

Referencia de implementación aprobada por **Dagt, 2026-11-03, opción B de OQ-021**: [comentario del propietario](https://github.com/Dagt/Fiscuvia/issues/52#issuecomment-6598264416). Aprueba VISUAL-SPEC, COMPONENTS-52, SCREENS-BASELINE y la comparación de #19; también DESIGN-SYSTEM y VISUAL-ACCEPTANCE. El collage original continúa ausente y no se afirma comparación con él. Los ADR no cambian de estado por esta aprobación.

## Arquitectura y estado real

**IMPLEMENTADO:** catálogo público de desarrollo `/desarrollo/componentes`; componentes compartidos en `web/src/shared/ui`; proveedores de presentación existentes; shell común en `web/src/app`; login y contexto autorizado en `areas/identity`. El catálogo no consulta APIs, almacena información ni representa un módulo fiscal. **DEMOSTRATIVO:** sus 125 registros, valores, estados y acciones locales. **PENDIENTE:** nombres/logos y provisioning #31, capacidades OQ-015, proveedor/MFA productivos OQ-014, módulos fiscales gate #74. **PROPUESTO:** pantallas de negocio de SCREENS-BASELINE. #56 mantiene pixel-diff, tolerancias y baselines independientes.

Se reutilizaron Brand, Button, PageHeader, Navigation, cinco estados, NotificationProvider y Radix Dialog/Slot. No se instala otra biblioteca, Storybook, RHF/Zod ni un sistema paralelo. Los formularios del catálogo sólo justifican controles nativos y validación demostrativa; los módulos futuros conservarán validación del servidor.

## Tokens y composición

`styles/tokens.css` concentra colores semánticos y roles inversos; `index.css` conserva la composición anterior en capa legacy, `components.css` define contratos compartidos y `catalog.css` sólo compone la demostración. No hay hex de color repartidos en componentes ni estilos de pantalla. Los roles inversos consolidan tonos decorativos históricos, sin una paleta por pantalla.

| Contrato | Implementación |
|---|---|
| Navegación | Navy #0C1D31, hover #18344F, selección también con borde/aria-current |
| Acción | Teal #087E80 / hover #07696B; indicador #0D9997 no sirve como fondo de texto blanco pequeño |
| Superficies | Canvas #F5F8FB, blanco #FFFFFF, borde decorativo #DCE5ED |
| Texto / control | #15283B / #536B80; borde de input y botón secundario #7C909F |
| Estados | Éxito #166859/E9F6F4, atención #855800/FFF7E5, error #A43535/FFF0EE, info #245D7B/EDF4F8; icono + texto |
| Tipografía | Inter Variable local, 28/24 título, 18 sección, 16 subtítulo, 14 cuerpo/tabla, 13 etiqueta, 12 ayuda; números tabulares |
| Geometría | Espacios 4/8/12/16/24/32/48, radios 8/12/6, control mínimo 44, login 48; sombra sólo overlays/aviso |
| Shell | Sidebar 240 desde 1024; header mínimo 66 escritorio / 64 móvil; contenedor máximo1440; padding32/24/16; una columna móvil |
| Breakpoints | Login640; contenido699; navegación1023. Los media queries CSS usan literales documentados, sin plugin para variables en media queries |

## Contratos TypeScript

Los tipos completos residen junto a cada componente y se verifican en modo estricto. Los slots ReactNode permiten iconos y composición sin una API paralela de estilos.

| Componente P0/P1 | Props/contrato efectivo | Estados e interacción |
|---|---|---|
| Brand | size sm/md, appearance light/dark | SVG decorativo + nombre; nombre accesible corresponde al enlace contenedor |
| AppShell / Navigation | children, authenticated, pageTitle, tenantContext, sessionActions | enlaces reales, pendientes disabled, ruta activa; menú Radix con Escape, foco confinado/restaurado; main único, salto y foco/scroll por pathname |
| AuthorizedTenantContext | memberships verificadas, onChoose, pending | 0/1/2+; nombres ordinales mientras #31 no entrega nombre comercial; sin UUID visible ni entrada arbitraria |
| PageHeader | title, description?, eyebrow?, breadcrumbs?, primaryAction?, secondaryActions?, action legado | h1 único, acciones wrap; eyebrow de desarrollo sólo en demostraciones |
| Button | primary/secondary/ghost/danger, default/outline compatibles, iconOnly, pending, disabled, asChild, props nativas | 44px; icon-only exige aria-label; pending bloquea envío; contenido/iconos por children; asChild conserva enlace |
| Field / Input | props nativas/ref, label, description?, error?, required, readonly/disabled | ids generados, labelFor, aria-invalid, ayuda/error asociados; sin depender del placeholder/color |
| Estados | Loading/Empty/Error/Permission/NotFound | copias seguras, título/ayuda y acciones según estado; first-use frente a vacío filtrado; Error exige onRetry, 404 no confirma recursos ajenos |
| ConfirmationDialog | trigger, title, description, confirmLabel, onConfirm async, destructive | estado interno de open/pending/error; cancelar inicial, Escape antes de enviar, bloqueo de doble envío y salida pendiente; error seguro conserva diálogo; foco restaurado |
| DataTable<T> | columns tipadas, rows de la página autorizada, rowKey/rowLabel, page/pageSize/total/onPageChange, sort/onSortChange, state/onRetry, renderMobile? | orden aria-sort y paginación controlados; elimina filas y conteos en loading/error/denied/empty; tabla semántica sticky en región desplazable rotulada; lista móvil con dt/dd; nunca autoriza/filtra datos protegidos |
| FilterBar | search/searchLabel/onSearchChange, SelectFilter[], onClear, activeCount, pending | disclosure móvil, conserva filtros; no persiste búsquedas/PII en URL; el consumidor decide cómo consultar datos autorizados |
| StatusPill | tone, children string | neutral/success/warning/danger/info, icono + texto, no deriva permisos/estado fiscal |
| StatCard | title, value?, description, state, onRetry?, action? | retira valor en carga/vacío/error/denied; nunca sustituye ausencia por cero; origen/fecha pueden componerse en description/action |
| SideDrawer | title, description, trigger, children, footer?, state?, onRetry? | Radix modal400/full mobile, scroll/Escape/trap/restore; loading/error/empty/denied retiran contenido y acciones anteriores, retry explícito; carga de lectura puede cerrarse sin bloquear al usuario |
| DetailsPanel | title, children | aside inline, sin trap ni modalidad; 320px lateral cuando cabe, debajo en espacio reducido |
| Notification | useNotification(string o {message,tone,action?}) | una notificación polite, cierre explícito44, no roba foco ni apila sin límite; no sustituye journal/confirmación |

Las variantes controladas externamente de dialog/drawer se difieren hasta un consumidor real que necesite abrirlos desde el router. Los disparadores, promesa de confirmación y contenido cubren la entrega actual sin props ficticias. No se introducen DateRange/FiscalPeriod/CalendarEvent/ObligationMatrix/EvidenceUploader/AuditTimeline/MoneyValue con semántica fiscal.

## Sesión y cambio de despacho

`useAuthorizedSession` obtiene `/api/v1/me` con cookie same-origin; muestra AppShell sólo tras identidad verificada. Para seleccionar: retira contexto visual, cancela y elimina queries de presentación protegida, solicita POST tenant+CSRF existente y vuelve a obtener identidad/membresías. Sólo muestra la respuesta si siguen coincidiendo actor, tenant, membership y rol. Una secuencia invalida respuestas tardías al cambiar/cancelar/desmontar/salir. Un 401 limpia cache y redirige a sesión expirada; 403 retira contexto y vuelve a consultar membresías. Cambiar despacho no otorga permisos; cada operación backend sigue autorizándose con RLS. No se almacena selección, token o credencial en localStorage.

Las consultas futuras deben consumir AbortSignal de TanStack y claves que incluyan identidad/tenant/capacidad/recurso/versiones de política. Limpiar cache es defensa de presentación, no autoridad. Durante revalidación de identidad el contexto no se representa como verificado. Logout conserva POST CSRF, invalidación del servidor y recarga limpia del navegador. El encabezado no ofrece búsqueda, notificaciones o perfil ficticios.

## Accesibilidad y aceptación

UX52-01–10 se trazan en [validación](evidence/issue-52/README.md). Reflow320 y texto200%, cuatro viewports, foco, roles, contraste axe, reduced-motion, teclas Escape/Tab, datos largos, conteos y errores cuentan con pruebas. Una revisión automática no certifica WCAG completo ni lector de pantalla. Los ejemplos son accesibles por enlaces internos; no hay trampa de foco fuera de overlays ni tabla diminuta móvil.

La comparación con #19 es contra el mismo producto antes del cambio y la especificación escrita aprobada. El collage ausente no permite pixel-diff. Los screenshots son evidencia de ejecución, no una aprobación automática del resultado.

## Handoff #31 y riesgos

#31 podrá componer Field/PageHeader/ConfirmationDialog/AppShell para provisioning, con sus propias APIs, autorización, idempotencia y pruebas. No debe convertir los ordinales en datos comerciales inventados ni reutilizar DEMO como contribuyentes. Nombres/logos reales requieren contrato y datos autorizados. No hay tema personalizable persistente ni panel administrativo implementado.

Las rutas de desarrollo son accesibles sin sesión y contienen exclusivamente ejemplos públicos estáticos. No conectar allí datos protegidos. La tabla no es un grid editable ni virtualización; recibe una página acotada. La validación local del formulario es demostrativa. Menús muy altos pueden desplazar verticalmente. El header permite crecer cuando el texto se amplía. Revisar las dependencias moderadas heredadas sin aplicar actualizaciones forzadas ajenas a #52.
