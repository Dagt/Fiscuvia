# Flujos de trabajo principales — WF-### (Proposed)

## WF-001 — Alta de despacho
Operador aprovisiona tenant -> owner recibe invitación segura -> configura identidad y roles -> crea cartera -> revisa políticas de plan. No acceso a documentos fiscales desde consola operador.

## WF-002 — Alta de contribuyente
Owner/contador autorizado captura RFC y datos, régimen y contacto -> comprueba formato y duplicados *dentro del tenant* -> asigna responsable -> crea expediente. Historial de cambios de régimen vigentes.

## WF-003 — Configurar obligaciones
Supervisor valida tipo de obligación/periodicidad/fecha/regla -> crea versión de plantilla -> asigna a contribuyentes/fechas efectivas -> previsualiza ocurrencias -> autoriza generación. No deducir normatividad de heurísticas sin verificación.

## WF-004 — Cierre mensual
Sistema genera ocurrencias idempotentes -> trabajo pendiente -> auxiliar prepara información -> adjunta evidencia -> envía a revisión -> supervisor aprueba/rechaza -> usuario autorizado registra presentación/acuse -> si hay impuesto a pagar registra evidencia de pago separada -> reporta estado y fecha de última verificación. Declaración puede ser presentada sin pago; complementar exige nueva evidencia/corrección.

## WF-005 — Control de certificados (sin secretos)
Contador registra fecha de vencimiento e.firma/CSD y origen del dato -> sistema alerta próximas fechas -> se confirma renovación/cambio y se audita. No cargar `.key` o contraseña.

## WF-006 — Solicitud y archivo
Contador sube documento -> validación/cuarentena -> private storage -> expediente y clasificación -> autorización de vista/descarga -> historial. En fase 3 cliente recibe solicitud, carga y consulta entregable compartido.

## WF-007 — Cobranza del despacho
Acuerdo de cuota -> cargo recurrente idempotente -> registrar pago parcial/completo -> recalcular saldo -> notificar atrasos si configurado -> ajustes compensatorios auditados. No relacionado con declaración SAT.

## WF-008 — Reporte y exportación
Usuario autorizado selecciona contribuyente/período/filtros -> backend aplica scopes -> exporta PDF/Excel con procedencia, estado y fecha -> firma temporal de descarga -> registro de auditoría. Jobs para grandes reportes.

## WF-009 — Importación CFDI (futuro)
Usuario autorizado carga XML/ZIP -> validación y extracción segura -> deduplicar UUID, clasificar emitido/recibido, vigencia con fuente -> reporte filtrable -> enlace a documentos; nunca asumir datos descargados = verdad actual SAT.

## WF-010 — Descarga SAT (futuro)
Consentimiento y firma autorizada según ADR -> solicitud asíncrona persistente -> verificar estado servicio -> descargar paquete -> validar/parsear -> notificar, con cuotas; fallos y reintentos recuperables. **No habilitado por MVP**.

## WF-011 — Conciliación asistida (futuro)
Importar movimientos bancarios de formato soportado -> proponer coincidencias con CFDI -> contador confirma/rechaza -> auditar diferencias. No cálculo fiscal automático.

## Contrato universal
Cada flujo: autenticación, membresía, capacidad, recurso/estado, transacción, evento, efectos asíncronos y UX (loading/error/retry). No registrar resultados ficticios como confirmados oficialmente.
