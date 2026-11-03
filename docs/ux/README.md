# UX por rol y flujos Fiscuvia (Proposed)

## Documentos
- [Biblioteca visual #19 aprobada por opción B OQ-021](reference-library/README.md)
- [Componentes y AppShell implementados #52](DESIGN-SYSTEM-52.md)
- [Evidencia y validación #52](evidence/issue-52/README.md)
- [Auditoría/capturas reales #19](reference-library/CURRENT-UI-AUDIT.md) y [contrato de implementación #52](reference-library/COMPONENTS-52.md)
- [Dirección visual y tokens](DESIGN-SYSTEM.md)
- [Concepto de seis pantallas aprobado (imagen aún no subida)](APPROVED-CONCEPT.md)
- [Mapa de rutas y estados](screen-inventory.md)
- [Experiencia del administrador del despacho](firm-admin-web.md)
- [Experiencia del contador/supervisor](accountant-operations-web.md)
- [Portal del contribuyente (Future)](taxpayer-portal-web.md)
- [Flujos transversales](UX-FLOWS.md)
- [Checklist visual de PR](VISUAL-ACCEPTANCE.md)

## Principios
1. Debe verse como producto SaaS comercial terminado; ningún scaffold genérico se considera entrega.
2. Primero contexto: despacho, rol, contribuyente y período. Acciones siempre específicas y autorizadas.
3. UI no introduce reglas fiscales por encima de máquina estados y backend.
4. Un panel mensual puede ser denso pero legible: filtros, estados, tablas y jerarquía visual.
5. 'En revisión', 'Presentada', 'Pagada' y 'Verificada SAT' no son sinónimos.
6. UI del contribuyente (Future) es radicalmente más simple que la interfaz interna.
7. Evidencia siempre con origen, fecha, actor y permisos cuando corresponda.
8. Responsive desde componentes, con alternativa móvil a grillas.
9. QA visual por pantallas reales, no mocks de imagen generada.

## Estados UX
**Required**: exigencia vinculada a FR/MVP aprobado cuando ocurra. **Proposed**: forma de interacción pendiente aprobación. **Open UX detail**: detalle que no cambia dominio y puede resolverse mediante test de usuario.
