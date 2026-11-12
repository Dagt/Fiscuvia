# SPEC01 — registro de aprobación limitada del propietario

**Estado: Accepted exclusivamente para el alcance inicial de #34; 2026-11-12.** Refs #74, PR #92. No acepta globalmente el modelo fiscal, los ADR, documentos/obligaciones/pagos ni la matriz de módulos posteriores. Esta entrega sigue siendo documental, sin grants, código productivo ni migraciones.

## Fuente y autoridad

Autoridad: **Dagt, propietario de Fiscuvia**, mediante instrucción expresa en esta conversación para continuar el PR #92, sin otra rama/PR y sin comenzar #34. Fuente primaria aportada: `Texto pegado.txt`, encabezado «FISCUVIA — FINALIZACIÓN DEL ISSUE #74», apartado «Las siguientes decisiones representan la autorización expresa del propietario para el alcance indicado». Fecha de recepción: **2026-11-12, America/Mexico_City**. HEAD expresamente revisado por el propietario: `260c53202a03a80597769f6fab80e348b0e437bd`.

SHA256 del archivo recibido: `b23b4570cde3512ff8ec95f81395606b0a6abd5293aad03d2a84ccccbeaedc4a`. Este registro transcribe el alcance para que la referencia sea durable en el repositorio; el hash identifica el adjunto, **no es una firma digital ni un comentario de GitHub del propietario**. Publicación y evidencia de revisión: [PR #92](https://github.com/Dagt/Fiscuvia/pull/92). No se fabrica una referencia issuecomment inexistente.

## Decisiones registradas

| ID | Fecha / autoridad / referencia | Alcance aprobado | Restricciones | Dependencias |
|---|---|---|---|---|
| D74-01 | 2026-11-12; Dagt; fuente anterior, sección1 | UUID estable, RFC normalizado único por tenant también archivado, mismo RFC permitido en otros tenants, ACTIVE/ARCHIVED, restore del mismo expediente e historial, validación local de formato/estructura | RFC/personType fuera de edición ordinaria; rechazar sólo los RFC genéricos documentados por valor exacto, sin regla de prefijos; ninguna verificación SAT. Corrección auditada futura con actor/fecha/motivo/unicidad/integridad y autorización propia, fuera de #34 | Constraints y pruebas de #34; excepciones RFC pendientes de contador y aprobación antes de implementarlas |
| D74-02 | 2026-11-12; Dagt; sección2 | Hasta10 referencias manuales de régimen por expediente, código de tres dígitos, varias simultáneas, vigencia conocida/desconocida, historial, MANUAL/PENDING_REVIEW | Sin catálogo normativo, CSF parser, verificación SAT, inferencias de obligaciones, periodicidad ni calendarios | OQ-006 sólo parcialmente autorizada; semántica fiscal/catálogo y reglas posteriores necesitan contador |
| D74-03 | 2026-11-12; Dagt; sección3 | CREATE/TENANT sólo owner propio con identidad/membresía/tenant ACTIVE, MFA≤10min y cuota; bootstrap UUID/perfil/PRIMARY/grants exactos VIEW/EDIT/ARCHIVE/ASSIGN, auditoría/outbox atómicos; VIEW/EDIT owner/supervisor/contador con grant exacto; archivo/restore sólo owner con grant+MFA; conflicto RFC genérico sin datos del existente | Auxiliar DENY; ninguna cartera global. ASSIGN sólo grant inicial reservado para #36, sin endpoints/política operativa de delegación en #34. Grants históricos no recuperan efectividad por reactivación/cambio de rol: revisión explícita autorizada antes de habilitarlos | Cercas SQL/autorizador y registro de habilitación versionado en futura #34; integración compatible con #33; revisión/gestión explícita en #36, sin nuevos permisos globales |
| D74-04 | 2026-11-12; Dagt; sección4 | #34 persistencia/API/lista básica/perfil/régimen/archivo/restore/principal inicial/autorización/cuotas/auditoría/contratos/pruebas; #35 directorio/formularios; #36 ficha/contactos/historial/múltiples responsables/cartera/certificados/actividad | No anticipar #35/#36 en #34. Gestión completa de contactos/responsables/sucursales diferida; FR-012/016 completos no se declaran entregados | Integrar este PR tras revisión final, latest main y PR #34 posterior; no comenzarlo en esta tarea |
| D74-05 | 2026-11-12; Dagt; sección5 | TAXPAYERS mide sólo ACTIVE; archivo libera, alta/restore requieren cuota vigente/disponible; dos competidores por última plaza no confirman ambos | Ausencia/vencimiento/agotamiento nunca es ilimitado; reducción no borra registros; consultar/editar no consume plaza; archivo permitido sin cupo. No reutilizar reservas USERS, no precios/billing paralelo | Medición/mutación atómicas con entitlements #32, lock de límite y pruebas de concurrencia #34 |

## Diferimientos expresamente aprobados

La sección6 autoriza diferir OQ-001/002/003/004/005/023 y lo restante de OQ-006/013/015. Sólo quedan autorizados captura manual de régimen, PRIMARY inicial y matriz limitada de contribuyentes. Las OQ completas permanecen **Open**. El contador debe validar las reglas de los módulos correspondientes antes de implementarlas.

Continúan excluidos: obligaciones automáticas, calendarios legales, declaraciones, pagos tributarios, revisión de obligaciones/evidencia fiscal definitiva, verificación SAT, custodia e.firma y correos productivos. Las aprobaciones administrativas anteriores #27/#31/#32/#33 no se amplían a esos módulos.

## Efecto y siguiente paso

Los documentos distinguen **decisión de producto autorizada** de **diseño técnico pendiente de implementación/validación DDL**. La vinculación de habilitaciones a la versión de membresía desarrolla el requisito obligatorio de no reactivación automática; no concede delegación ni autoriza un endpoint de revisión en #34. Este PR prepara revisión final y merge por el propietario; permanece Draft por el protocolo vigente. **No se autoriza a este agente a iniciar #34, marcar Ready, hacer merge, desplegar o cerrar issues.** Tras integrar #92, el siguiente trabajo será #34 sobre latest main, con esta autorización acotada como referencia y pruebas nuevas del SHA correspondiente.
