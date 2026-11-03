# Criterios visuales bloqueantes para PRs frontend
El usuario considera inaceptable repetir frontends funcionales pero visualmente genéricos. La calidad visual es requisito de entrega, no un extra.
- Respetar tokens, identidad, shell y componentes compartidos en DESIGN-SYSTEM.md.
- A cada cambio UI adjuntar screenshots reales tomados de la aplicación ejecutándose: escritorio 1440×900 y móvil 390×844; documentar también vacío/error y contenido realista sintético.
- Comparar con referencia de seis pantallas (login, dashboard, contribuyentes, detalle, CFDI, calendario). Mientras falta el original, usar la [propuesta escrita #19](reference-library/README.md) para revisión humana; **no** llamarla aprobada hasta decisión explícita OQ-021. Referencia conceptual ≠ evidencia de implementación; no afirmar comparación pixel a pixel con imagen ausente.
- Revisar escala tipográfica, alineación, espacios, densidad útil, acciones, iconos, consistencia, texto ES-MX, scroll/overflow, foco visible y contraste.
- No dar por terminadas tablas sin filtros y jerarquía, formularios desnudos, componentes stock sin identidad, navegación improvisada o pantallas bilingües.
- Rubrica 1–5: fidelidad de marca, jerarquía, legibilidad, densidad, responsive, accesibilidad, estados y facilidad de tarea. <4 exige corrección o excepción humana documentada.
- Capturas sin datos personales reales; describe discrepancias e iteraciones en PR. Ningún agente puede aprobar su propio merge.

#19 aporta [capturas y procedencia](evidence/issue-19/README.md), [comparación verificable](reference-library/CURRENT-UI-AUDIT.md) y [criterios medibles de #52](reference-library/COMPONENTS-52.md). Axe y contrastes calculados cubren comprobaciones concretas, no certificación completa WCAG. #56 definirá pixel-diff/umbrales y política de baselines; no se declara completado por estas capturas de auditoría.
