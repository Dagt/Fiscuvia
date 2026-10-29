# Matriz de evidencia, hipótesis y decisiones

**Regla:** no elevar 'supuesto' a 'Confirmado' ni 'Aceptado' sin evidencia o decisión de producto.

| ID | Afirmación | Estado | Evidencia / siguiente acción |
|---|---|---|---|
| EV-001 | El proyecto se denomina Fiscuvia | Confirmed por propietario | Conversación 2026-10-29 |
| EV-002 | Eslogan 'Todo tu despacho, una sola vía.' | Confirmed por propietario | Conversación 2026-10-29 |
| EV-003 | Producto SaaS con Java/Spring Boot/React/PostgreSQL | Confirmed como dirección de trabajo | Conversación 2026-10-29; versiones exactas propuestas |
| EV-004 | Referencia seis pantallas estilo navy/teal limpio | Confirmed como dirección visual | Imagen conceptual aprobada en conversación; archivo binario falta subir |
| EV-005 | Despacho usa una herramienta para CFDI, CONTPAQi para contabilidad y HTML para seguimiento | Confirmed sólo como relato | Audio verbal; observación/UAT pendiente |
| EV-006 | Descarga directa SAT es técnicamente viable | Proposed | Verificar servicio y condiciones vigentes al abordar E13 |
| EV-007 | Despachos pagarían planes propuestos | Assumption | Investigación de precios; OQ-007 |
| EV-008 | PDF/Excel más calendario son decisivos | Assumption informada | Priorizar entrevista de uso real |
| EV-009 | Fiscuvia sustituirá CONTPAQi | Rejected por enfoque | No objetivo de MVP |
| EV-010 | Guardar e.firma en SaaS | Open y prohibido en MVP | OQ-009 y ADR-0003 |
| EV-011 | Aplicación multitenant con RLS | Proposed ADR | Requiere pruebas y aprobación de ADR |
| EV-012 | Datos reales aptos para piloto | Open | Sólo tras gates de seguridad, privacidad y restauración |

## Inconsistencias/puntos no resueltos
- Marca exacta del descargador (OneFactor vs OneFacture) no está confirmada.
- 'Pago de impuestos' ≠ 'pago de honorarios'; UI y modelos separados.
- Estado capturado por empleado ≠ aceptación/validación SAT.
- Vencimiento de e.firma/CSD (metadatos) ≠ almacenamiento de llave o contraseña.
