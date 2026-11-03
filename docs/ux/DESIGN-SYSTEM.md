# Sistema visual de Fiscuvia v0.1 — obligatorio
**Marca:** Fiscuvia. **Eslogan:** Todo tu despacho, una sola vía. Personalidad: B2B confiable, profesional, refinado, moderno, cálido y de altísima legibilidad; no dashboard genérico ni interfaz gubernamental antigua. La imagen conceptual de seis pantallas aprobada en conversación es inspiración, no captura de software existente.

## Referencia aprobada e implementación #52

OQ-021 **Resolved para implementación visual**: Dagt aprobó opción B el 2026-11-03 ([decisión explícita](https://github.com/Dagt/Fiscuvia/issues/52#issuecomment-6598264416)). [Referencia escrita](reference-library/VISUAL-SPEC.md), [contratos #52](reference-library/COMPONENTS-52.md), [seis pantallas](reference-library/SCREENS-BASELINE.md) y [auditoría anterior](reference-library/CURRENT-UI-AUDIT.md). El collage continúa ausente. La [guía efectiva #52](DESIGN-SYSTEM-52.md) documenta tokens, props, estados, sesión, diferencias y límites; prevalece sobre los ejemplos históricos siguientes. No aprueba ADR, permisos ni reglas fiscales.

## Tokens implementados
Fuente única: `web/src/styles/tokens.css`. Navy #0C1D31/hover #18344F; teal #087E80/hover #07696B; indicador #0D9997; lienzo #F5F8FB, superficie #FFFFFF, línea decorativa #DCE5ED, borde de control #7C909F, tinta #15283B, secundario **#536B80**. El histórico #667A8F se retira por contraste insuficiente. Éxito #166859/E9F6F4, atención #855800/FFF7E5, error #A43535/FFF0EE, info #245D7B/EDF4F8; icono/texto además del color. #0D9997 no sirve como fondo de texto blanco pequeño; #DCE5ED no sustituye borde accesible de input.
## Tipografía y composición
Inter local; números tabulares; h1 28/24 móvil, sección18, subtítulo16, cuerpo14, etiqueta13 y ayuda12. Espaciado4/8/12/16/24/32/48; radio8/12/6; control mínimo44 y login48. Sombra restringida a overlays/avisos; Lucide consistente. Foco3px/offset4 (navegación offset1). No sacrificar legibilidad por densidad.
## Shell
Sidebar navy240 desde1024, header≥66 desktop/64 móvil; contenido máximo1440 con padding32/24/16. Mismo shell para desarrollo y sesión verificada. Contexto sólo tras autorización real; sin búsqueda, alertas, perfil ni nombres comerciales ficticios. Header puede crecer con texto ampliado. Un main, un h1, salto/foco/scroll y drawer accesible.
## Componentes reutilizables obligatorios
Catálogo `/desarrollo/componentes`: Brand, Navigation, AuthorizedTenantContext, PageHeader, Button, Field/Input, cinco estados, ConfirmationDialog, DataTable, FilterBar, StatusPill, StatCard, SideDrawer/DetailsPanel y Notification. Tabla125 DEMO paginada, nombres100/cadena64/números grandes. RHF/Zod se reserva para un formulario real que lo justifique. DateRange/FiscalPeriod/CalendarEvent/ObligationMatrix/EvidenceUploader/AuditTimeline/MoneyValue siguen propuestos para sus módulos; no tienen semántica fiscal implementada aquí.
## UX
CTA visible, jerarquía y datos numéricos alineados a derecha. Filtros no persisten PII en URL. En móvil lista etiquetada; en desktop tabla con scroll interno rotulado/sticky. Loading/error/empty/permission, foco/teclado y ES-MX. No inventar confirmaciones SAT. #31 provisioning/marca, #56 pixel-diff y gate #74 siguen independientes.
## Reglas
Frontend no queda terminado hasta capturas **reales del navegador** (1440×900 y 390×844) y revisión visual. Sin reemplazar evidencia de app con mockups IA; anotar desviaciones. Ver VISUAL-ACCEPTANCE.md.
