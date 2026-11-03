# Biblioteca visual aprobable — UX-00 / #19

**Estado: propuesta para revisión humana.** Base inspeccionada: main `3621a7d1e319f6ec8bbedad5db015d4a98a8591c` (#86). La épica de seguridad está integrada para local/test; eso no aprueba UX, proveedor productivo ni reglas fiscales. Refs #19; NFR-030–036, OQ-021, ADR-004/011 **Proposed**.

## Lectura y autoridad

| Etiqueta | Significado |
|---|---|
| DIRECCIÓN APROBADA | Fiscuvia, eslogan, navy/teal, Inter, Lucide, superficies claras, sobriedad/legibilidad, ES-MX; autorización del propietario en las instrucciones de producto |
| PROPUESTA POR APROBAR | Medida, token exacto, composición o contrato de esta biblioteca; requiere revisión antes de adoptarlo como baseline |
| IMPLEMENTADO | Existe en el frontend inspeccionado; no equivale a aprobación visual ni funcionalidad fiscal |
| PENDIENTE | Componente o integración aún no implementados; issue responsable explícito |
| CONCEPTO FUTURO | Pantalla de negocio documentada; no captura de producto, dato real ni autorización para construirla ahora |

Una fila puede decir IMPLEMENTADO + PROPUESTA POR APROBAR para conservar una medida actual como candidato. **Ningún hex, captura o componente se vuelve aprobado por estar en Git o por pasar axe.** Si el collage se recupera, resolver diferencias con el propietario antes de #52; no sobrescribir esta propuesta silenciosamente.

1. [Especificación visual y tokens](VISUAL-SPEC.md): valores candidatos, contraste y composición.
2. [Contratos e inventario para #52](COMPONENTS-52.md): prioridad, props, variantes, estados y pruebas.
3. [Las seis pantallas y estados](SCREENS-BASELINE.md): layout propuesto, dependencias y fronteras.
4. [Auditoría de la UI real](CURRENT-UI-AUDIT.md): capturas actuales, medidas, diferencias y destinos.
5. [Evidencia real, procedencia y comandos](../evidence/issue-19/README.md).

## Registro del asset original — REF-ORIGINAL-01

**FALTANTE.** El collage conceptual de seis pantallas sigue sin archivo verificable. Ruta prevista, aún inexistente: `docs/ux/assets/fiscuvia-six-screens-reference.png`. No se crea un placeholder con ese nombre.

Búsqueda limitada al proyecto: assets versionados y no ignorados, imágenes del árbol local (incluidas copias locales de evidencia), índice Git y `git log --all -- docs/ux/assets`. Sólo se localizaron favicon y screenshots reales de #21/#27; las copias de scanners en `.ci-work` no son originales nuevos. Issue #19 no contiene adjuntos/comentarios de referencia en la consulta inicial. No se inspeccionaron cuentas/carpetas ajenas ni se descargó una plantilla ni se generó una imagen con IA. **No hubo comparación pixel a pixel con el collage ausente.**

Al recibirlo, registrar propietario/autorización, fecha, origen legítimo, archivo, SHA256 y qué aspectos se aprueban; confirmar que no contiene PII/credenciales. Si corresponde, conservarlo como referencia conceptual, separado de screenshots de producto. No deducir permisos o estados fiscales de una imagen.

## Decisiones concretas del propietario

- **A — aportar el collage original:** revisar su procedencia y contrastar esta especificación antes de aprobar el baseline. OQ-021 permanece Open mientras falta esa revisión.
- **B — aprobar expresamente la especificación escrita:** aceptar esta revisión de VISUAL-SPEC, COMPONENTS-52 y SCREENS-BASELINE como fuente de implementación, con los ajustes que indique el propietario. No requiere inventar/reconstruir el collage.
- Confirmar valores propuestos de estados/borde de controles, escala tipográfica, geometría y densidad; las capturas actuales sirven para evaluar diferencias, no se proponen como aprobación automática del aspecto completo.

Registrar decisión, alcance, fecha, propietario y comentario/PR de autorización. **Ahora no hay selección A/B ni aprobación final y OQ-021 sigue Open.** El merge de documentación por sí solo no cierra esa pregunta. #52 y #31 no se implementan ni se declaran completados. #56 conserva baseline pixel-diff/umbrales y automatización visual futura.

## Fronteras para próximas entregas

#52 implementa componentes y composición tras aprobación de esta referencia; #31 aporta nombres/logos/configuración autorizados de despachos; la shell no crea ni inventa esa información. #35/#36, #41, #47, #51/#53/#55 y #63 aportan funciones de dominio en sus propios PRs. Gate #74 y OQ-015 continúan vigentes. Ocultar un control no sustituye autorización del backend.

## Trazabilidad y alcance de evidencia

Los [NFR de UX](../../architecture/non-functional-requirements.md) conservan Proposed; esta tabla no los acepta por iniciativa del agente. [DoD](../../delivery/DEFINITION-OF-DONE.md) y [workflow](../../backlog/codex-workflow.md) siguen aplicando.

| Referencia | Entrega concreta #19 | Pendiente explícito |
|---|---|---|
| NFR-030 | VISUAL-SPEC e inventario: identidad autorizada separada de valores candidatos | aprobación de referencia final y componentes #52 |
| NFR-031 | 17 PNG reales1440/390, fuente Git/tree/hash/dimensiones en manifest | revisión humana de dirección; no comparación con original ausente |
| NFR-032 | pares de contraste calculados, axe en17 vistas y checks teclado/menú/overflow | no certificación WCAG completa; campos/tablas/zoom por componente #52 y #56 |
| NFR-033 | UI existente y mensajes propuestos ES-MX | textos definitivos de módulos futuros |
| NFR-034 | contrato DataTable móvil/lista y criterios UX52-03/05 | tabla móvil productiva no implementada; no se declara PASS |
| NFR-035 | auditamos vistas sin métricas fiscales; cinco pantallas de negocio son conceptos | fuente/estado fiscal real y revisión #74 en sus módulos |
| NFR-036 | errores/sesión reales y galería demo rotulada, contrato de estados por pantalla | conflictos/validaciones y estados específicos de cada módulo futuro |
| OQ-021 / ADR-004/011 | registro de asset faltante, alternativas A/B y propuesta revisable | decisión explícita del propietario; ADR siguen Proposed |
