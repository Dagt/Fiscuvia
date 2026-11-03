# Inventario y contrato de entrega — #52 pendiente

Estado: PROPUESTA POR APROBAR; **#19 no implementa este catálogo**. Las props son contratos de diseño, no nuevas APIs/backend ni un mandato de instalar paquetes. [Especificación común](VISUAL-SPEC.md), [pantallas](SCREENS-BASELINE.md), [diferencias reales](CURRENT-UI-AUDIT.md).

## Inventario priorizado

| Prioridad / componente | Existe hoy | Contrato candidato para #52 | Estados y verificación |
|---|---|---|---|
| P0 Tokens/typography | tokens.css + Inter; hex extra en CSS | roles semánticos de VISUAL-SPEC, escalas compartidas y modo de contraste estable; sin tema por pantalla | pares de color efectivos, focus/hover/selected/disabled, reduced-motion |
| P0 Brand | SVG+texto propio | mantener marca/eslogan; size sm/md, variante dark/light; nombre accesible en enlace, icono decorativo | claro/oscuro, nombre largo de despacho separado de marca |
| P0 AppShell/Navigation | sólo /desarrollo, Radix mobile | children, navigation de rutas reales, activePath del router, tenantContext autorizado, sessionActions; no actor/roles arbitrarios como autorización | desktop/tableta/móvil, sin contexto/sin membresía/error, foco al navegar, menu Escape/trap/restore |
| P0 AuthorizedTenantContext | selector real en /app, no en Shell | membresías verificadas, selectedId, onSelect, pending/error; label comercial/logo sólo si contrato #31 lo entrega | 0/1/2+ membresías, nombre 100 caracteres, revocación, cambio autorizado; no persistir datos previos |
| P0 PageHeader | title/description/action, eyebrow fijo desarrollo | title, description?, eyebrow?, primaryAction?, secondaryActions?, breadcrumbs?; un h1 por vista | multilínea/ayuda ausente, acciones wrap, 24/28px; eliminar eyebrow técnico de vistas de negocio, conservarlo en demo |
| P0 Button | default/outline/ghost; Radix Slot/cva | variantes primary/secondary/ghost/danger, size standard/icon, pending, disabled, leading/trailingIcon, asChild | 44px, icon-only aria-label, pending sin doble click/cambio ancho, foco y texto ≥4.5:1; link sigue siendo link |
| P0 Field/Input | no visible reutilizable; login sólo CSRF oculto | id, label, description?, error?, required?, disabled?, type, value/onChange; native props/ref; labelFor, aria-invalid/describedby | requerido/opcional, invalid, disabled, readonly, nombres largos, autofill; borde.control, error con texto; no formulario local de contraseña |
| P0 Loading/Empty/Error/Unauthorized/NotFound | cinco estados reales reutilizables + galería | title, description, action?; Error onRetry; Busy aria-busy/status; distinguir first-use/filtered-empty/403/404 | no ilustración fiscal inventada, retry real o demo rotulado, no revelar recurso ajeno, foco/live sólo al cambiar |
| P0 ConfirmationDialog | no; menú usa Dialog, que no es confirmación | open/onOpenChange, title, description, confirmLabel, onConfirm, pending, destructive | Cancelar inicial para destructivo, Escape antes de envío, retorno foco, error de servidor conserva contexto; Radix AlertDialog si se justifica instalar |
| P1 DataTable | no | columns con id/header/cell/sortability/alignment; rows tipadas, rowKey interno, pagination/sort controlados por servidor, state, onRetry, mobileRenderer | loading/0/error/403, count autorizado, sticky encabezado, aria-sort, acciones nombradas; sin filtros/client paging que amplíen acceso |
| P1 FilterBar | no | search label/value/onChange, filters tipados, appliedCount, onClear, pending; estado serializable seguro en URL | wrap en tablet, disclosure móvil sin perder filtros/resultado; no PII sensible en URL; limpiar resultado tras cambio contexto |
| P1 StatusPill | demo-badge específico, no componente general | label, tone neutral/success/warning/danger/info, icon?; map de estado validado por módulo | texto+forma/icono, sin click si sólo informativo; no estado fiscal ni permiso derivado del color |
| P1 StatCard | no | label, value autorizado, supportingText?, timestamp/source?, href? sólo funcional, state | loading/empty/error/denied; no cero ficticio ni sparkline decorativa; máximo 4 métricas pertinentes en una fila amplia |
| P1 SideDrawer/DetailsPanel | no (menú no es panel de recurso) | open/onOpenChange, title/description, content, actions?, pending/error; recurso ya autorizado | lateral 400px candidato, ancho completo ≤699; foco/restauración, cuerpo scrolleable, footer no tapa contenido; alternativa ruta completa |
| P1 Notification | aviso status existente | message, tone, dismiss?, action?; sólo eventos reales o demo rotulada | anuncia una vez, cerrar 44px, no apilar sin límite; no sustituye confirmación ni journal durable |

## Densidad, tabla y composición

SideDrawer es **modal** con overlay, foco confinado, Escape y retorno al disparador; ancho400px candidato, máximo viewport, a390 ocupa ancho completo. DetailsPanel es **inline/complementario**, ancho320–360px cuando quepa en la grid; no overlay ni trampa de foco, y se apila debajo del contenido en móvil. Ninguno concede acceso al recurso. Si el detalle requiere interacción extensa, preferir una ruta autorizada propia; no abrir modales anidados.

Fila estándar 52px como mínimo candidato, header44px, padding celdas 12px vertical/16px horizontal; modo compacto44px con controles táctiles44px y sin cortar texto. Tipografía14px. Razón social columna flexible min220px, RFC futuro min144px monoespaciado/tabular si ayuda, acciones44px. Columna de importes derecha; header y dato conservan asociación. Tablas anchas pueden scrollear **dentro de un contenedor rotulado y enfocable**, nunca ensanchar toda la página. Móvil ofrece lista de registros con campos etiquetados, acción y enlace a detalle; no copiar una tabla con fuente diminuta.

100+ filas sintéticas en catálogo **sólo de desarrollo**, con paginación y valores “DEMO”, para probar densidad/nombres largos/teclado. No presentarlas en dashboard o módulos fiscales operativos. El catálogo #52 no crea endpoints fiscales ni simula autorización. Caso no asignado puede representarse como 404 según política existente, sin confirmar que el recurso existe.

## Límites y dependencias

Reutilizar React/TS, React Router, Tailwind, Lucide, Radix Dialog/Slot y cva ya fijados; TanStack Query para datos reales. shadcn es patrón de composición existente, no autorización de copiar tema stock. No instalar UI suites, Storybook, bibliotecas de gráficos/tabla, nuevos estados globales o date pickers sin uso concreto y revisión de bundle/accesibilidad. RHF/Zod sólo cuando aparezca un formulario real que los justifique; no están instalados hoy. Se puede construir catálogo aislado con rutas explícitas de desarrollo y componentes de datos sintéticos.

**No implementar en #52:** CRUD/directorio/ficha, cálculo/estados fiscales, KPIs de negocio, branding persistente o provisioning #31, matriz de permisos #28/33, auditoría lectora, worker/exportación/descarga, integración CFDI/SAT o personalización de Keycloak productivo. FiscalPeriod, DateRange, CalendarEvent, ObligationMatrix, EvidenceUploader, AuditTimeline y MoneyValue quedan como contratos especializados a concretar en sus módulos; #52 aporta tipografía y composición, no su semántica fiscal ni datos.

## Aceptación medible para #52 (tras aprobación humana)

| ID | Comprobación requerida |
|---|---|
| UX52-01 | Un catálogo explícitamente de desarrollo con todos los componentes P0 y P1 realmente entregados, props/variantes documentadas; pendientes visibles, no declarados implementados |
| UX52-02 | 1440×900 y1280×800: sidebar240, header≥66, padding32, contenido útil1136/976 con tolerancia1px por redondeo; no overflow global |
| UX52-03 | 768×1024: menú drawer y padding24; 390×844: padding16, una columna, header≥64 y destinos44px; controles no se superponen |
| UX52-04 | Login conserva BFF/OIDC/MFA/cookies/CSRF/logout; /app sólo integra shell tras datos autorizados, sin dashboard ficticio ni nombre/logo inventado |
| UX52-05 | 100+ filas DEMO paginadas, nombre100 caracteres y cadena sin espacios64, etiquetas largas, cantidades grandes sólo en catálogo; sin truncamiento irrecuperable ni mezcla carteras |
| UX52-06 | Cada componente aplicable muestra loading/empty/error/denied/disabled/pending/focus/hover; lista filtrada vacía se diferencia de first-use y de acceso denegado |
| UX52-07 | Teclado completo, skip-link, dialogs con Escape/trap/restore; nombres accesibles, errores asociados y estados anunciados; capturas de foco y revisión manual |
| UX52-08 | Contraste medido AA en pares actuales, axe sin violaciones A/AA aplicables, 200% texto/zoom, reflow320px, reduced-motion; alcance manual registrado, sin certificar todo WCAG por axe |
| UX52-09 | Capturas reales 1440/390 y estados con SHA/tree de UI, sin PII/token/credenciales; comparación contra referencia escrita aprobada o collage recuperado |
| UX52-10 | Mantener suites completas y cinco jobs success sobre head final; #56 define aparte pixel-diff, tolerancias, baselines revisados y políticas de actualización |

La aprobación de este contrato no aprueba roles, reglas fiscales, endpoints o proveedor productivo. Si una combinación propuesta no funciona con la evidencia, presentar ajuste visual para aprobación en vez de cambiar auth/RLS por motivos de layout.
