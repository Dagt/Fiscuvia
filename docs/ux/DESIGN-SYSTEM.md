# Sistema visual de Fiscuvia v0.1 — obligatorio
**Marca:** Fiscuvia. **Eslogan:** Todo tu despacho, una sola vía. Personalidad: B2B confiable, profesional, refinado, moderno, cálido y de altísima legibilidad; no dashboard genérico ni interfaz gubernamental antigua. La imagen conceptual de seis pantallas aprobada en conversación es inspiración, no captura de software existente.

## Consolidación aprobable #19

La [referencia escrita candidata](reference-library/VISUAL-SPEC.md) distingue DIRECCIÓN APROBADA, PROPUESTA POR APROBAR e IMPLEMENTADO/PENDIENTE/CONCEPTO FUTURO. [Inventario y contratos #52](reference-library/COMPONENTS-52.md), [seis pantallas](reference-library/SCREENS-BASELINE.md), [auditoría real](reference-library/CURRENT-UI-AUDIT.md). OQ-021 permanece Open: estos documentos no aceptan automáticamente los valores históricos de abajo ni convierten el collage ausente en evidencia.

Discrepancia comprobada: CSS usa secundario #536B80; el #667A8F histórico no llega a4.5:1 sobre blanco. #0D9997 y #DCE5ED son acentos/separadores, no texto blanco pequeño o borde único de input. Los candidatos de contraste y nuevos tokens semánticos están en la referencia para aprobación, sin modificar CSS en #19.
## Tokens (propuestos; ajustar contrastes en implementación)
Navy sidebar #0C1D31, navy-hover #18344F; teal primario #087E80 y seleccionado #0D9997; ámbar sobrio #D79C32; lienzo #F5F8FB, superficie #FFFFFF, línea #DCE5ED, tinta #15283B, secundario #667A8F. Semánticos éxito/advertencia/error/info con icono/texto además del color; contraste WCAG 2.2 AA.
## Tipografía y composición
Inter; números tabulares; h1 24–28px, secciones 16–18, cuerpo 14 y auxiliares 12–13 (nunca sacrificar legibilidad). Espaciado 4/8/12/16/24/32; radio 8–12; bordes sutiles; sombras mínimas, nada de gradientes exagerados. Control estándar 40–44px, objetivos táctiles 44px. Íconos Lucide consistentes.
## Shell
Sidebar navy plegable 224–248px, barra superior 60–68px con búsqueda, despacho autorizado, alertas y usuario. Contenido desktop padding 24–32px, móvil 16px. Mismo shell para todas las pantallas.
## Componentes reutilizables obligatorios
AppShell, PageHeader, StatCard, StatusPill, DataTable (densidad ajustable, sticky header, paginación), FilterBar, DateRange, FiscalPeriod, SideDrawer, DetailsPanel, CalendarEvent, ObligationMatrix, EvidenceUploader, AuditTimeline, MoneyValue, Empty/Error/Loading states, ConfirmationDialog. Formularios con RHF+Zod y controles consistentes.
## UX
CTA visible, jerarquía, filtros persistentes en URL, tablas de importes alineados a derecha, estados claros. En móvil usar tarjetas/alternativa a tabla ancha; no apretar columnas. Keyboard/focus, skeleton/error/empty/permission. Todos los textos ES-MX; no combinar etiquetas inglés/español. No inventar datos ni confirmaciones SAT.
## Reglas
Frontend no queda terminado hasta capturas **reales del navegador** (1440×900 y 390×844) y revisión visual. Sin reemplazar evidencia de app con mockups IA; anotar desviaciones. Ver VISUAL-ACCEPTANCE.md.
