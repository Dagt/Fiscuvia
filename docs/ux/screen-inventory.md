# Inventario de rutas, pantallas y estados UX — propuesta v0.1

**Estado actual #52 (prevalece sobre la enmienda histórica #19):** OQ-021 Resolved para implementación visual, Dagt opción B 2026-11-03. Se añade `/desarrollo/componentes`, catálogo público DEMO sin APIs; `/app` comparte AppShell tras verificar identidad/membresías. [Contratos efectivos](DESIGN-SYSTEM-52.md), [capturas/comparación](evidence/issue-52/README.md). Las demás rutas de negocio siguen propuestas; no se autorizan capacidades ni nombres comerciales de #31.

**Propósito:** evitar replicar el problema de TrailerSaaS de mejorar UX después de la construcción. Ruta marcada Future no se debe simular como funcional.

**Enmienda #19:** la tabla siguiente es planeación propuesta de producto/roles, no autorización ni lista de módulos implementados. Owner no obtiene AUDIT_VIEW o capacidades fiscales por esta tabla; OQ-015/#74 siguen vigentes. Rutas reales: `/login`, `/app` (sesión/membresías), `/desarrollo`, `/desarrollo/estados` y fallback404. El callback OIDC real es backend `/api/v1/auth/callback/local`, no página React `/auth/callback`. [Matriz UI actual](reference-library/CURRENT-UI-AUDIT.md), [criterios de seis conceptos](reference-library/SCREENS-BASELINE.md), [contratos #52](reference-library/COMPONENTS-52.md). OQ-021 Open.

| Ruta / familia | Roles | Pantallas y estados requeridos | MVP |
|---|---|---|---|
| `/login`, callback backend `/api/v1/auth/callback/local` | anónimo | acceso/SSO real, expiración, reintento, recuperación desde proveedor configurado | ✓ |
| `/app` | colaborador autorizado | KPIs, próximos vencimientos, mis tareas, actividad, estados loading/empty/error | ✓ |
| `/app/taxpayers` | staff | listado/search/filtros/paginación, alta, duplicado, permisos | ✓ |
| `/app/taxpayers/:id` | staff asignado | resumen, obligaciones, documentos, honorarios autorizado, historial, certificados metadata | ✓ |
| `/app/obligations` | staff | calendario/lista/matriz; detalle, presentaciones y pagos separados | ✓ |
| `/app/obligations/:id` | staff asignado | checklist, evidencia, revisión, presentación, pago y timeline | ✓ |
| `/app/tasks` | staff | personal/equipo, asignación, fechas, estado, revisión, recurrencia | ✓ |
| `/app/documents` | staff | subir, clasificación, preview, cuarentena, private download | ✓ |
| `/app/fees` | owner/billing | cargos, pagos, aging, ajustes, roles y reportes | ✓ |
| `/app/reports` | staff autorizado | presets, filtros, export job status, acceso controlado | ✓ |
| `/app/settings` | owner | firma/datos, plan, plantillas, configuración, notificaciones | ✓ |
| `/app/settings/members` | owner/manager | invitación, roles, cartera, suspensión y confirmación | ✓ |
| `/app/audit` | owner/privileged | eventos, filtros, read-only, paginado | ✓ |
| `/app/cfdi` | staff | emitidos/recibidos, importación/descarga/estado + filtros | Future |
| `/app/cfdi/:id` | staff | panel detalle XML/metadatos/status con fuente | Future |
| `/app/sat-downloads` | staff | jobs, progreso, reintento, límites y errores | Future |
| `/portal`, `/portal/requests`, `/portal/documents` | contribuyente delegado | pendientes, upload, entregables, estado | Future |
| `/admin/tenants` | operador plataforma | provisioning/planes/estado sin acceso fiscal | MVP administración interna, no suplantación |

## Estados para cada pantalla
- *First use* (vacío total), vacío filtrado, loading/skeleton, error recuperable y retry, 401/403/404, 409 conflicto, 5xx, cuota 429, nombres largos, montos grandes, múltiples roles, RFC mismo entre despachos.
- Viewports: 390×844, 768×1024, 1280×800, 1440×900; revisar 1920 para tablas y 320 px edge case.
- E2E/screenshot: mostrar datos sintéticos, no cliente real. Cada ruta enlaza con FR, estado y issue de implementación en PR correspondiente.
- No permitir que un menú tenga enlaces a módulos no existentes como si ya operaran; usar feature flags o sección 'próximamente' expresamente no interactiva si aprobada.

## Entrega local #27

`/login`: acceso OIDC real, carga/configuración/error/expiración; `/app`: identidad real, sin membresías, selección autorizada y logout. Los ordinales de despacho corresponden a membresías reales; aún no existe edición de nombres/CRUD. No hay dashboard fiscal ni login simulado. [Capturas reales y revisión responsive](evidence/issue-27/README.md). #52/#56 y las demás pantallas conservan su alcance independiente.
