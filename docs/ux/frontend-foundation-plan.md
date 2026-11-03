# Plan maestro UX/UI Fiscuvia (Proposed)

**Referencia metodológica:** docs/ux/frontend-redesign-master-plan.md del proyecto TrailerSaaS, pero aplicado **antes de construir** la interfaz. La referencia visual aprobada es el collage de seis pantallas Fiscuvia con navy/teal; **NO adoptar Fleet Simple** porque la dirección de TrailerSaaS (sidebar clara/azul) fue específica de ese producto.

## Objetivo
Una plataforma moderna y visualmente sólida en primer PR de UI, no un diseño correctivo post-MVP. Claridad, densidad operativa, velocidad de escaneo, identidad premium y precisión fiscal.

## Inventario y trazabilidad
Mantener [screen-inventory](screen-inventory.md) con vista/estado, actor, permisos, FR y issue. Las primeras seis pantallas guía: login, dashboard, clientes, detalle, CFDI (Future), calendario. No dibujar datos CFDI ficticios como producto implementado.

## Oleadas visuales
UX-00 (#19): recuperar archivo aprobado **o aprobar expresamente especificación escrita alternativa**; [biblioteca candidata](reference-library/README.md), tokens/composición1440/390 y auditoría real. OQ-021 sigue Open hasta decisión del propietario. No implementa UX-01/#52 ni #31.
UX-01: fundación React, tokens, tipografía, shell, nav, botones, inputs, tablas, cards, date period, empty/loading/error/unauthorized.
UX-02: login y onboarding de despacho con OIDC real y permisos.
UX-03: directorio y ficha del cliente; filtros, detalle, alertas metadata.
UX-04: obligaciones calendario/lista/matriz; revisión, pago y evidencia en drawer/detalle.
UX-05: documentos, honorarios, tareas, dashboard y reportes.
UX-06 (Future): centro CFDI y portal del contribuyente.
Cada fase en una o varias issues revisables; **no alterar backend/tenancy ni fiscal state machine como 'arreglo visual' sin issue específico**.

## Checklist visual y funcional (hard gate)
- Typography scale, contrast WCAG 2.2 AA, color semántico + texto, foco/teclado, estados, labels ES-MX, responsive 390/768/1280/1440/1920.
- Capturas navegador real, con datos sintéticos y versión SHA, no mockups IA.
- Mostrar tabla dense de 100+ registros y estados bajo carga/empty/error, pruebas overflow, 200% zoom y sin hover-only control.
- Los screenshots se revisan frente al concepto y tokens, documentando diferencias antes del Ready for Review.
- No inventar KPIs, CFDI vigentes, validaciones SAT, obligaciones legales o flujos de aprobación no implementados.

## Criterios de aceptación del kit
Consistencia entre 5 rutas del MVP, sin mezcla idiomática, componentes reutilizables demostrables, pruebas E2E de navegación/permisos, visual review humano. Abusar de tarjetas/badges/gráficos no equivale a visual premium.
