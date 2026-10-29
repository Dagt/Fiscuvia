# Definition of Done — obligatoria
Todo PR listo para revisión debe:
1. Cumplir criterios del issue y documentar lo que queda fuera.
2. Compilar, pasar typecheck, lint, tests unitarios, integración y E2E aplicables; reportar comandos/resultados exactos y estado real de CI.
3. Verificar roles, permisos por recurso, RLS y denegación entre despachos; no incluir secretos ni PII en fixtures/logs.
4. Acompañar migraciones Flyway probadas en PostgreSQL real; estrategia segura de despliegue y recuperación.
5. Actualizar OpenAPI, modelos y documentación si cambia contrato o comportamiento.
6. Cubrir validaciones, errores, reintentos, idempotencia y auditoría cuando corresponda.
7. Cumplir diseño Fiscuvia, accesibilidad y UX ES-MX; mostrar screenshots de aplicación real desktop/móvil y estados vacío/error.
8. Usar datos sintéticos y evidencia reproducible; no declarar verde CI remota no verificada.
9. Adjuntar archivos cambiados, riesgos, limitaciones y pasos de prueba.
10. Mantener PR **Draft** y **no hacer merge** hasta aprobación humana.
## Gates por módulo
Identidad/RLS: dos tenants con pruebas negativas; documentos: control de acceso y carga hostil; obligaciones: períodos y estados separados de presentación/pago; honorarios: BigDecimal, saldos y auditoría; CFDI: XML endurecido, duplicados y procedencia; SAT: ADR de llaves y revisión legal aprobadas.
