# Referencia escrita de identidad y composición — v1 aprobada para #52

Estado: **APROBADA por Dagt el 2026-11-03, opción B OQ-021**, [comentario explícito](https://github.com/Dagt/Fiscuvia/issues/52#issuecomment-6598264416). Las menciones históricas a candidato/propuesta en las tablas se entienden aprobadas para la implementación visual #52; no aceptan ADR ni funciones fiscales. [Implementación efectiva](../DESIGN-SYSTEM-52.md). Se preserva navy/teal/Inter/Lucide/ES-MX; no Fleet Simple. Los tokens efectivos provienen de [tokens.css](../../../web/src/styles/tokens.css).

## Paleta y semántica

| Rol semántico candidato | Valor | Estado / uso permitido |
|---|---|---|
| navigation.background / hover | #0C1D31 / #18344F | IMPLEMENTADO; mantener navy, nunca sidebar clara como identidad alternativa |
| action.primary / hover | #087E80 / #07696B | IMPLEMENTADO; texto blanco, acción primaria |
| selection.indicator | #0D9997 | IMPLEMENTADO; indicador/borde/acento, **no** fondo con texto blanco pequeño |
| selection.surface | #E9F6F4 | IMPLEMENTADO; texto oscuro teal/ink, selección también por borde/icono/aria-current |
| canvas / surface | #F5F8FB / #FFFFFF | IMPLEMENTADO; lienzo común y paneles útiles, evitar tarjeta por cada dato |
| border.subtle | #DCE5ED | IMPLEMENTADO; separador decorativo, no única delimitación de control interactivo |
| text.primary / secondary | #15283B / #536B80 | IMPLEMENTADO; sustituye el secundario documental #667A8F, que sigue siendo propuesta histórica |
| border.control | #7C909F | PROPUESTA POR APROBAR / PENDIENTE; borde identificable de input/checkbox/selector sobre blanco |
| focus.light / focus.dark | #087E80 / #8CF0E0 | IMPLEMENTADO en controles/nav; anillo visible sin depender sólo de sombra |
| status.success text/bg | #166859 / #E9F6F4 | PROPUESTA POR APROBAR para StatusPill; verde actual en identidad no implica catálogo implementado |
| status.warning text/bg | #855800 / #FFF7E5 | PROPUESTA POR APROBAR / PENDIENTE |
| status.error text/bg | #A43535 / #FFF0EE | IMPLEMENTADO para error; generalización semántica pendiente |
| status.info text/bg | #245D7B / #EDF4F8 | PROPUESTA POR APROBAR / PENDIENTE |
| accent.amber | #D79C32 | Propuesta histórica; sólo acento no textual hasta resolver combinación accesible |

Tokens nuevos semánticos deben mapearse a estas funciones en #52; no añadir hex arbitrario por pantalla. Éxito/alerta/error/información siempre requieren texto + icono o forma. No mapear un color a una regla fiscal aún no aprobada ni usar “Verificado SAT” sin procedencia.

### Contrastes calculados (sRGB, razón sin redondear para decidir)

| Frente / fondo | Razón aproximada | Conclusión |
|---|---|---|
| #087E80 / #FFFFFF | 4.88:1 | Texto normal AA |
| #0D9997 / #FFFFFF | 3.49:1 | No texto normal; sí indicador no textual donde aplique |
| #536B80 / #F5F8FB | 5.21:1 | Secundario pequeño sobre lienzo |
| #667A8F / #FFFFFF | 4.43:1 | Propuesta histórica insuficiente para texto normal |
| #DCE5ED / #FFFFFF | 1.27:1 | Separación decorativa; no borde único de input |
| #7C909F / #FFFFFF | 3.31:1 | Candidato borde de control |
| success / fondo propuesto | 6.00:1 | Texto normal AA |
| warning / fondo propuesto | 5.80:1 | Texto normal AA |
| error / fondo actual | 6.04:1 | Texto normal AA |
| info / fondo propuesto | 6.46:1 | Texto normal AA |

Método: luminancia relativa sRGB, `(Lclaro + .05)/(Loscuro + .05)`. #52 debe probar pares efectivos (incluidos hover/focus/selección), opacidad y superposiciones. Tablas de color y axe sin violaciones **no certifican toda WCAG**.

## Tipografía, ritmo y superficie

- Inter Variable autoalojada, fallback system-ui/sans-serif; ya importada por main.tsx. Lucide 18–20px en controles, 24px en señales; iconos decorativos aria-hidden, icon-only con nombre accesible. Marca actual SVG propia existente; no se inventa logo nuevo.
- Escala candidata: título de página 28px/1.35 semibold (24 móvil); sección 18px/1.4; subsección 16px/1.4; cuerpo y tabla 14px/1.6; ayuda 12px/1.6; labels 13px/1.5 semibold. Login conserva título de marca 46px máximo desktop, 29 móvil, y acceso 28/24. Peso normal 400, medio 500, semibold 600. No usar peso/color como único nivel semántico.
- Datos monetarios futuros con cifras tabulares y alineación derecha; formato es-MX según moneda entregada, no deducir pagos SAT de honorarios. Evitar gráficas decorativas. Estado sin datos no se representa con un KPI ficticio cero.
- Escala 4/8/12/16/24/32/48px. Gap de campos 16; de secciones 24 (32 en amplia); labels 8px de su control. Botones/inputs estándar 44px mínimo, CTA acceso 48. Tamaño de texto mínimo de producto 12px: es una política propuesta, **no** un mínimo tipográfico impuesto por WCAG.
- Bordes 1px; radio controles 8px, panel/tarjeta 12px, pill 6px; sombras sólo overlay/aviso (`0 8px 24px #15283B15`) y elevación de modal, no en toda fila. No gradientes como base de la navegación.
- Hover real sólo si interactivo; active/selected distintos de hover. Disabled impide activar y explica el motivo donde sea útil; pending mantiene label, aria-busy y evita doble envío. Error con mensaje asociado al campo, nunca sólo rojo. Foco visible de 3px, offset 4px general/1px nav oscura, sin quedar cubierto por header/drawer.

## Composición y responsive candidato

| Zona | Desktop 1440×900 / 1280×800 | Tableta 768×1024 / móvil 390×844 |
|---|---|---|
| Sidebar | 240px navy fija, scroll propio si hace falta, enlaces 46px; seleccionada con aria-current y barra teal | Debajo de 1024px: botón menú 44px, drawer min(304px, viewport−32px), overlay y cierre accesible; sin mini-sidebar estrecha |
| Header | Alto mínimo 66px, padding horizontal 32px, contexto/breadcrumb y acciones reales | 64px mínimo, padding 12 móvil/24 tableta; título/selector nunca se superponen; crecer si texto exige |
| Workspace | ancho restante; contenedor máximo 1440px incluido padding, centrado | 100% disponible, min-width:0 |
| Contenido | 32px de padding; a 1440 quedan 1136px útiles, a 1280 quedan 976px con sidebar | Horizontal24px tableta →720px útiles; horizontal16px móvil →358px; vertical24px candidato; scroll vertical legítimo |
| Grid | 12 columnas, gap 24px; formulario principal máx. 720px, secundario 320–360 cuando quepa | 8 columnas tableta/4 móvil como regla de composición; móvil una columna de lectura, gap16, no tablero comprimido |
| PageHeader | título/ayuda a izquierda, una CTA principal y secundarias a derecha, gap24 | apilar título→ayuda→acciones; CTA a ancho completo sólo cuando ayude; nunca esconder primaria en hover |
| Login | panel navy 42%, contenido máximo460px, sin sidebar de módulos; ningún campo de contraseña dentro de Fiscuvia | ≤640px panel de marca arriba y acceso en columna; padding24; mantener CTA visible o alcanzable mediante scroll |

La grid es contrato de composición propuesto, no una grid CSS ya implementada. Breakpoints candidatos: 640 login, 700 componentes de contenido, 1024 navegación; consolidar diferencias actuales 640/699/850/1023/1199/1600 sin cambiar identidad. A 1920, el máximo impide líneas interminables; a 320px no debe perderse contenido. 200% texto/zoom y reflow a 320 CSS px se verifican por separado.

## Navegación y contexto autorizado

Sólo enlaces operativos; futuros no interactivos y rotulados. Una búsqueda global, campana o avatar no se añaden como adornos si no tienen comportamiento real. El selector de despacho recibe únicamente membresías del backend; nunca inventa nombre/logo ni confunde rol con permisos universales. Estado sin membresías ofrece explicación/invitación, no alta pública. Hasta #31, “Despacho 1” es ordinal técnico real, no nombre comercial. Cambiar contexto revalida y limpia datos del anterior; eso es contrato de integración futura, no función nueva de #19.

## Accesibilidad medible

Objetivo [WCAG 2.2 AA](https://www.w3.org/WAI/WCAG22/quickref/): 1.4.3 texto normal ≥4.5:1, texto grande ≥3:1; 1.4.11 señales/controles relevantes ≥3:1; 1.4.10 reflow; 2.4.7/2.4.11 foco visible/no oculto; 2.5.8 tamaño mínimo de destino 24 CSS px con sus excepciones. **Fiscuvia propone 44×44px para controles principales/táctiles**, más exigente que ese mínimo AA. No atribuir focus appearance AAA a AA.

Navegación completa con teclado, skip-link, orden DOM coherente; diálogos con título/descripcion, foco contenido, Escape y retorno al disparador. Lectores de pantalla con labels y errores asociados, aria-live sólo para cambios pertinentes. Respeto reduced-motion; no animación necesaria para entender. Es-MX y mensajes accionables. Probar zoom/reflow, contraste, nombres largos y tamaños táctiles manualmente además de axe. La operación de identidad y MFA se conserva, no se rediseña el proveedor.
