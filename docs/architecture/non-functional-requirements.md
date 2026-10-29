# Requisitos no funcionales NFR-### (Proposed)

## Identidad y seguridad
- **NFR-001** Todos los datos tenant-owned deberán aislarse por servidor y PostgreSQL RLS.
- **NFR-002** Toda mutación deberá pasar autorización por rol y recurso además de tenant.
- **NFR-003** Servicio de aplicación PostgreSQL nunca usará superuser/BYPASSRLS.
- **NFR-004** Ninguna credencial e.firma/SAT se almacenará en browser ni repo.
- **NFR-005** TLS obligatorio en todos los ambientes remotos; reposo cifrado.
- **NFR-006** Acceso a archivos será privado, temporal y autorizado por objeto.
- **NFR-007** MFA para miembros con capacidades privilegiadas.
- **NFR-008** Todos los secretos/PII redactados en logs, trazas, métricas y evidencias QA.
- **NFR-009** Acciones sensibles llevarán actor, tenant, tiempo y correlación auditables.

## Rendimiento y escalabilidad
- **NFR-010** Listados usan paginación/índices tenant; no cargar clientes de todos los despachos.
- **NFR-011** Carga prolongada/exports/jobs no bloquearán API síncrona.
- **NFR-012** Debe existir medición real de tiempos de respuesta antes de fijar SLO/latencias de piloto.
- **NFR-013** La plataforma soportará cuotas por tenant y crecimiento sin cambiar su concepto de tenancy.
- **NFR-014** Carga XML/ZIP futura será streamed y protegida de zip bombs/XXE.
- **NFR-015** Métricas de consumo de almacenamiento, jobs, usuarios y RFC ayudarán a controlar costos.

## Integridad/reliability
- **NFR-020** Mutaciones reintentables tendrán idempotencia y límites.
- **NFR-021** Comandos críticos usarán transacciones y control de concurrencia/version.
- **NFR-022** Estado de tarea/obligación debe coincidir con eventos/auditoría.
- **NFR-023** Backups cifrados automatizados y restauración ensayada antes de piloto real.
- **NFR-024** Jobs se recuperarán tras reinicio sin doble ejecución lógica.
- **NFR-025** Errores externos se aislarán con reintento/backoff acotado y estado visible.
- **NFR-026** Suspensión de suscripción no borrará datos sin una política aprobada.

## UX accesibilidad y apariencia
- **NFR-030** Fiscuvia debe respetar identidad visual aprobada: navy/teal, tipografía, componentes comunes.
- **NFR-031** Revisión visual con screenshots reales 1440×900/390×844 y estados representativos será obligatoria para UI.
- **NFR-032** Objetivo WCAG 2.2 AA y navegación mediante teclado/foco visible.
- **NFR-033** Toda UI final para usuarios mexicanos estará localizada es-MX.
- **NFR-034** Móviles presentarán alternativas operables a tablas densas.
- **NFR-035** No afirmar estados oficiales SAT ni cifras no obtenidas de fuente real.
- **NFR-036** Feedback de errores, permisos denegados, loading, conflicto y vacío explícito.

## Operaciones, soporte, cumplimiento
- **NFR-040** Builds CI reproducibles enlazados a SHA final; sin afirmar CI remota verificada cuando no lo está.
- **NFR-041** Entornos y secretos de local/staging/prod independientes.
- **NFR-042** Retención, privacidad, portabilidad, eliminación y DPA requieren aprobación legal.
- **NFR-043** Alertas, métricas, logging estructurado y runbooks operativos antes de piloto.
- **NFR-044** Validar dependencias y escanear secretos/vulnerabilidades.
- **NFR-045** Incidentes deben poder investigarse sin exponer datos fiscales o llaves privadas.
