# Criterios visuales bloqueantes para PRs frontend
El usuario considera inaceptable repetir frontends funcionales pero visualmente genéricos. La calidad visual es requisito de entrega, no un extra.
- Respetar tokens, identidad, shell y componentes compartidos en DESIGN-SYSTEM.md.
- A cada cambio UI adjuntar screenshots reales tomados de la aplicación ejecutándose: escritorio 1440×900 y móvil 390×844; documentar también vacío/error y contenido realista sintético.
- Comparar con referencia aprobada de seis pantallas (login, dashboard, clientes, detalle, CFDI, calendario). Referencia conceptual ≠ evidencia de implementación.
- Revisar escala tipográfica, alineación, espacios, densidad útil, acciones, iconos, consistencia, texto ES-MX, scroll/overflow, foco visible y contraste.
- No dar por terminadas tablas sin filtros y jerarquía, formularios desnudos, componentes stock sin identidad, navegación improvisada o pantallas bilingües.
- Rubrica 1–5: fidelidad de marca, jerarquía, legibilidad, densidad, responsive, accesibilidad, estados y facilidad de tarea. <4 exige corrección o excepción humana documentada.
- Capturas sin datos personales reales; describe discrepancias e iteraciones en PR. Ningún agente puede aprobar su propio merge.
