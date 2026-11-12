# SPEC01 — permisos de #34 aprobados y propuestas posteriores

**Aprobación limitada D74-03 por Dagt, 2026-11-12; Refs #74.** [Fuente/alcance/restricciones](../decisions/issue-74-owner-approval.md), [decisiones/OQ](../product/SPEC01-decisions-74.md). Ninguna fila instala políticas/grants en este PR. La [infraestructura #28](RESOURCE-AUTHORIZATION.md) y la [administración #33](TEAM-ADMINISTRATION-33.md) se conservan; su integración fiscal se implementará posteriormente.

## Cuatro estados de decisión

- **Ya aprobado:** base OIDC/membresía/tenant y permisos administrativos anteriores; además las cuatro operaciones #34 y su bootstrap exacto indicados abajo, con la restricción obligatoria de no reactivar grants históricos automáticamente.
- **Diseño para implementar:** habilitación fiscal vinculada a versión de membresía, FK y cercas; materializa D74-03 sin nuevos permisos globales. No es código ejecutado ni prueba DDL.
- **Propuesto/pendiente:** gestión de cartera/revisión explícita #36, documentos, obligaciones, auxiliares, separación de funciones y excepciones unipersonales. Ninguna fila de esos módulos se aprueba por extensión.
- **Bloqueado en esta tarea:** código, migraciones y grants; no comenzar #34 hasta revisión final e integración de #92 según instrucción del propietario. Efectivo actual en V1–V11 para fiscal: **DENY**.

## Matriz aprobada exclusivamente para #34

`E` = capacidad del rol **más grant exacto efectivamente habilitado para la versión vigente de membership y recurso/operación**, nunca cartera completa. `M` = además MFA confiable reciente ≤10min en entorno aprobado. `—` = DENY aprobado para este corte. Son políticas autorizadas para futura implementación, no permisos efectivos actuales.

| Operación | Owner | Supervisor | Contador | Auxiliar | Alcance autorizado |
|---|---|---|---|---|---|
| TAXPAYER_CREATE / TENANT propio | M | — | — | — | Identidad/membresía/tenant ACTIVE, cuota vigente/disponible, comprobación backend |
| TAXPAYER_VIEW / TAXPAYER item o colección | E | E | E | — | Sólo contribuyentes con grant efectivo exacto, antes de filtros/paginación |
| TAXPAYER_EDIT / TAXPAYER | E | E | E | — | Perfil/régimen de ACTIVE; RFC/personType fuera de edición ordinaria |
| TAXPAYER_ARCHIVE / TAXPAYER | E+M | — | — | — | Archivo y restore mismo UUID; restore revalida cuota |
| Administración TAXPAYER_ASSIGN | Diferida | — | — | — | #34 sólo crea el grant inicial reservado; no política operativa ni endpoint de delegación/revisión |

Bootstrap autorizado: UUID generado por servidor, PRIMARY del creador y cuatro grants VIEW/EDIT/ARCHIVE/ASSIGN sólo para esa membresía y expediente. ASSIGN se conserva como grant reservado para #36; **no instalar en #34 una fila operativa role_capabilities TAXPAYER_ASSIGN que adelante gestión de cartera**. Su futura política/comando debe revisarse en #36. Supervisor/contador no reciben grants automáticamente por tener rol permitido; #34 no publica un mecanismo para concedérselos. Auxiliar sigue DENY.

## Matriz de módulos posteriores — Proposed, no aprobada

| Operación / código propuesto | Owner | Supervisor | Contador | Auxiliar | Corte |
|---|---|---|---|---|---|
| Asignar responsables/accesos TAXPAYER_ASSIGN | E+M | — | — | — | #36; #34 sólo bootstrap exacto de alta, sin endpoint de delegación |
| Leer documento DOCUMENT_VIEW | E | E | E | — | #46–48 y contexto de expediente; clasificación permitida explícita |
| Descargar DOCUMENT_DOWNLOAD | E | E | E | — | Aprobación separada; VIEW no basta |
| Cargar evidencia DOCUMENT_UPLOAD | E | E | E | Pendiente, DENY | #46, tipos/campos mínimos OQ-015 |
| Configurar obligaciones OBLIGATION_CONFIGURE | E | E | E | — | #39; política revisada, sin inferencia por régimen |
| Preparar OBLIGATION_PREPARE / SUBMIT_REVIEW | E | E | E | Pendiente, DENY | #43/#44; grant por operación |
| Aprobar/rechazar/reabrir OBLIGATION_APPROVE / REJECT / REOPEN | E+M | E+M | — | — | #44/OQ-005; no autoaprobación implícita |
| Registrar presentación OBLIGATION_RECORD_FILED | E | E | E | — | #45, evidencia/excepción aprobada |
| Registrar impuesto TAX_PAYMENT_RECORD | E | E | E | — | #45; independiente de honorarios |
| Consultar auditoría AUDIT_VIEW | — | — | — | — | Sigue pendiente; historia de perfil no abre journal |
| Honorarios FEE_* | Pendiente | Pendiente | Pendiente | — | #49–51/OQ-011/015, sin heredar de impuestos |
| Exportar TAXPAYER_EXPORT / REPORT_EXPORT | — | — | — | — | Diferido; VIEW no concede exportación |

El catálogo documental incluye códigos todavía ausentes de `Capability`, `ResourceTarget.Kind` y constraints SQL. No declararlos disponibles por aparecer aquí. Añadir los códigos/categorías necesarios sólo en el PR del módulo aprobado, con guardas, mapeo de diagnóstico y pruebas. No hay nuevas capacidades globales de cartera ni roles nuevos: FIRM_BILLING/portal del PRD no existen en los cuatro roles iniciales y quedan diferidos. TAXPAYER_CREATE es facultad acotada de inserción, no lectura tenant-wide.

## Bootstrap y deduplicación de #34

Sin recurso existente no puede haber grant item. D74-03 aprueba la política TAXPAYER_CREATE/TENANT sólo owner propio+MFA; función de alta genera UUID, crea expediente/PRIMARY y **únicamente** grants VIEW/EDIT/ARCHIVE/ASSIGN para esa membresía y UUID. Las habilitaciones iniciales VIEW/EDIT/ARCHIVE se registran para su versión/rol vigentes; ASSIGN queda reservado sin política operativa #34. No recibe acceso a expedientes anteriores ni de otros creadores, documentos o revisión fiscal. Ni invitación, reactivación ni cambio de rol crea/revalida grants fiscales.

Todo se confirma atómicamente con evento/outbox/recibo. Contexto de alta no sirve para leer cartera previa ni mutar grants arbitrarios. Respuesta mínima y GET posterior con nueva autorización VIEW. La excepción de inserción y ampliaciones RLS materializan el bootstrap aprobado; #34 debe probarlas sin relajar resource_fence. Replay de CREATE no habilita de nuevo grants previos ni actualiza su revisión/versionado.

UNIQUE tenant/RFC genera una señal de conflicto si el RFC existe fuera de la cartera del creador. D74-03 aprueba esa señal genérica, sin ID/nombre/estado/responsable/cartera del registro. Mensaje fijo “No se pudo registrar el contribuyente; solicita revisión al responsable autorizado”. No lookup público/global ni cross-tenant query; SQLSTATE/constraints no salen al cliente.

## Grant existente y habilitación efectiva — regla obligatoria

Un grant histórico conservado **no es un grant habilitado**. Para cada operación: sesión/identidad ACTIVE → tenant ACTIVE → membresía ACTIVE → rol/capacidad vigente → grant exacto activo → revisión de habilitación vigente → MFA si corresponde → estado/versión del recurso. Una fila existente, un rol recuperado o un botón visible nunca sustituyen estos pasos.

Diseño técnico derivado de D74-03: guardar revisiones append-only por clave exacta del grant con decisión ENABLE/DISABLE, secuencia, versión de membresía revisada, rol interno revisado, actor/fecha/comando/evento. Sólo la última revisión ENABLE, con `reviewed_membership_version=identity.memberships.version` y rol vigente coincidente, puede habilitarlo. Ausencia, DISABLE, versión obsoleta o rol ambiguo/desconocido deniega. No seleccionar una revisión antigua ENABLE después de un DISABLE ni aceptar la última revisión de otra versión. [Campos/FK y locks](../data-model/schema-v0.1.1-proposed.md).

V11 incrementa membership.version al suspender/revocar/reactivar y al cambiar rol mediante team_command (actualiza membresía antes de membership_roles). Es una versión monotónica, no se restaura al regresar al mismo rol/estado. Ejemplo: revisión ENABLE versión7 → suspensión8 → reactivación9: conserva grant/revisión7 pero sigue denegado hasta revisión explícita para9; cambiar contador→auxiliar→contador también queda denegado. Usar esa versión evita un contador paralelo y conserva las respuestas/estados/cuotas de #33. Cualquier productor futuro de cambios de rol debe avanzar esa versión en la misma transacción; no habilitar una ruta SQL administrativa que omita esa invalidación. Migrador/DBA comprometido mantiene el límite de confianza documentado en #28.

La futura #34 debe aplicar esta condición **tanto al reunir/bloquear grants en authorize_operation como en resource_allowed/RLS y comandos definer**, sólo para categoría TAXPAYER, sin cambiar semántica de otras categorías. Un grant obsoleto no entra en el conjunto congelado de colección y devuelve404 para item; colección autorizada sin habilitaciones efectivas queda vacía. Reautorizar cada solicitud y replay; ningún replay de un comando fiscal o administrativo renueva habilitaciones. Runtime/sesiones no pueden insertar revisiones ni modificar grants directamente.

#34 sólo emite las revisiones de bootstrap de VIEW/EDIT/ARCHIVE para el nuevo expediente, en el mismo commit. **No ofrece endpoint de revisión, rehabilitación, delegación ni bypass de recuperación**. Tras una invalidación, el acceso antiguo permanece DENY hasta el flujo explícito y autorizado de #36, cuyo contrato/política aún debe aprobarse. No diseñar un auto-review al reactivar, al iniciar sesión o al usar de nuevo el expediente. Si un owner sin otra autoridad efectiva requiere recuperación, hace falta procedimiento administrativo controlado con aprobación específica futura; no autoconcesión ni permiso global nuevo en #34.

## Responsabilidad, concesión y cambios de equipo

Assignment funcional no equivale a resource_grant. #36 propone dos operaciones explícitas por expediente: cambio de responsables con cierre/apertura de intervalo, y cambio de acceso que selecciona conjunto de capacidades permitido por esta matriz. No permitir strings libres, roles del cliente, ALL, listas globales ni acceso a otro tenant. Requiere TAXPAYER_ASSIGN exacto, MFA y actor autorizado; no autodelegación ilimitada ni conceder operaciones fuera de la matriz aprobada. Retiro conserva grant inactivo/historial en vez de borrar atribución. Un grant activo no basta si el rol/membresía ya no permite operación.

Cambiar responsabilidad sin tocar grants deja acceso previo vigente hasta revocación explícita: mostrarlo en confirmación #36, no afirmar que reasignar retiró acceso. El owner puede seguir con stewardship exacto del expediente aun si otro es principal. Si se desea un comando conjunto de reasignación+revocación, debe declarar los dos efectos, comprobar todos sus targets y registrar un hecho compuesto tipado en la misma transacción; no usar listener asíncrono que deje una ventana de acceso.

| Escenario | Efecto exigido / propuesta pendiente |
|---|---|
| Miembro SUSPENDED | Autoría/relaciones/grants se conservan; autorización ACTIVE falla. No reasignar ni dar acceso al sustituto automáticamente |
| Miembro REVOKED | Terminal por #33, no reactivar ni invitar para eludirlo; historial permanece. Gestión explícita de cartera pendiente #36 |
| Reactivación SUSPENDED | V11 conserva reactivación administrativa/cuota, pero la nueva versión invalida habilitaciones fiscales previas. ACTIVE no rehabilita grants; revisión explícita posterior obligatoria, no disponible en #34 |
| Cambio de rol | Reevalúa capacidad y cambia versión de membresía; aunque recupere el rol original, habilitación anterior sigue obsoleta. Cambio de rol no revalida ni crea grants |
| Reasignación | Cerrar relación y abrir nueva bajo lock taxpayer; target ACTIVE, identidad ACTIVE y mismo tenant; permisos se gestionan explícitamente |
| Usuario en varios despachos | MembershipId y grants diferentes, incluso mismo userId/RFC; no copiar cartera entre tenants |
| Único integrante | Con D74-03 puede crear y mantener sus expedientes; no se otorga OBLIGATION_APPROVE ni revisión independiente por ser owner. OQ-005 separado |
| Despacho suspendido / usuario global DISABLED | Nuevas operaciones protegidas fallan; no borrar registros. Revalidar en backend y limpiar UI al conocer denegación |
| Operación concurrente con revocación | Gana el orden de locks/commit vigente; no cancelación retroactiva. Autoridad congelada sólo durante ese callback, nunca cacheada para el siguiente |

## Lecturas, RLS y UX

Colección VIEW aplica grants y RLS antes de filtros/count/orden/paginación; sin grants devuelve vacío. Item ajeno/inexistente/no asignado o sin autoridad de item devuelve 404 uniforme según IdentityStore vigente; colección sin capacidad403, sesión inválida401. No exponer por qué falló MFA/asignación de un item. Restore/edición sin autoridad tampoco filtra estado ni versión.

Documento necesita su propio grant y pertenencia de expediente; no pasar de VIEW taxpayer a DOWNLOAD document dentro de un contexto xid inmutable. Diseñar función/puerto específico de documento que verifique ambos alcances sin cache de permisos. Export/jobs reautorizan con política propia; no elevar outbox a actor. frontend usa sólo acciones que backend devuelve para la presentación **como indicios no reutilizables**; el comando siempre reautoriza. #34 no agrega endpoint genérico de consulta de permisos.

Pruebas #34: matriz aprobada por fila, dos tenants/mismo RFC sintético y carteras separadas, owner sin grant, MFA/CSRF, grants sin revisión/obsoletos/DISABLE y regreso al rol anterior. Verificar en HTTP y SQL sin WHERE que suspender→reactivar y cambiar→recuperar rol siguen denegando sin revisión; replay no renueva habilitación y rollback administrativo no invalida un cambio que no confirmó. Oráculo negativo: omitir la comparación de versión en autorizador o RLS debe detectarse. Pruebas de documentos y demás capacidades quedan en sus módulos sin aprobación implícita. Cero grants productivos en #74.
