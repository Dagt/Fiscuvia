# SPEC01 — matriz fiscal por recurso, propuesta

**Proposed; Refs #74; ninguna fila instala políticas/grants.** [D74-03/OQ-015](../product/SPEC01-decisions-74.md). La [infraestructura #28](RESOURCE-AUTHORIZATION.md) y la [aprobación limitada #33](TEAM-ADMINISTRATION-33.md) se conservan.

## Cuatro estados de decisión

- **Ya aprobado:** OIDC/sesión, membresía ACTIVE, cuatro roles internos, tenant y autorización fresca; permisos limitados de settings/plan/equipo según V7/V9/V11 y sus referencias, MFA confiable reciente. No es aprobación fiscal.
- **Propuesto:** matriz siguiente, alcance exacto, bootstrap de #34 y gestión de acceso #36.
- **Pendiente de decisión:** todas las asignaciones fiscales a roles y recursos; datos mínimos/documentos de auxiliar, separación de funciones y excepciones unipersonales.
- **Bloqueado:** implementar políticas fiscales, grant productivo, nuevas migraciones de dominio y aprobación de actividades mientras falte aprobación correspondiente. Efectivo actual para todas las filas fiscales: **DENY**.

## Matriz para revisión del propietario

`E` = sólo capacidad aprobada **más grant exacto por membership/recurso/operación**, nunca cartera completa. `M` = también MFA confiable reciente ≤10min en entorno aprobado. `—` = DENY propuesto. Estos símbolos no son permisos actuales.

| Operación / código propuesto | Owner | Supervisor | Contador | Auxiliar | Corte |
|---|---|---|---|---|---|
| Alta TAXPAYER_CREATE, target TENANT del despacho propio | M | — | — | — | #34; código existente, combinación nueva por aprobar |
| Consultar TAXPAYER_VIEW, target TAXPAYER item/collection | E | E | E | — | #34; lista sólo filas asignadas |
| Editar perfil/régimen TAXPAYER_EDIT | E | E | E | — | #34, ACTIVE, sin cambios de RFC/personType |
| Archivar/restaurar TAXPAYER_ARCHIVE | E+M | — | — | — | #34, código de recurso en catálogo documental, falta Java/SQL |
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

Sin recurso existente no puede haber grant item. Propuesta: owner+MFA recibe política TAXPAYER_CREATE/TENANT del propio tenant; función de alta genera UUID, crea el expediente, PRIMARY del creador y **únicamente** grants VIEW/EDIT/ARCHIVE/ASSIGN para esa membresía y ese UUID. No recibe permisos sobre contribuyentes anteriores, documentos, revisiones fiscales ni nuevos clientes creados por otra persona. Ni invitación ni cambio de rol dispara grants fiscales.

Todo se confirma atómicamente y con evento/recibo. Contexto de alta no sirve para listar/leer otros expedientes ni mutar grants arbitrarios. La respuesta es recibo mínimo; GET posterior exige nueva autorización VIEW. Aprobar matriz sin aprobar este bootstrap no hace implementable el alta. La excepción de inserción y ampliaciones de constraints RLS son parte de D74-03 y del review de migración #34; no relajar resource_fence de lectura.

UNIQUE tenant/RFC genera una señal de conflicto si el RFC existe fuera de la cartera del creador. No devolver ID, nombre, estado, responsable, archivo ni pertenencia del registro existente. Mensaje fijo “No se pudo registrar el contribuyente; solicita revisión al responsable autorizado”. No lookup público de RFC ni cross-tenant query. El propietario debe aceptar explícitamente esa señal mínima de dedupe para el rol creador, o cambiar el flujo a revisión administrativa privada. Los SQLSTATE y nombres de constraint no salen al cliente.

## Responsabilidad, concesión y cambios de equipo

Assignment funcional no equivale a resource_grant. #36 propone dos operaciones explícitas por expediente: cambio de responsables con cierre/apertura de intervalo, y cambio de acceso que selecciona conjunto de capacidades permitido por esta matriz. No permitir strings libres, roles del cliente, ALL, listas globales ni acceso a otro tenant. Requiere TAXPAYER_ASSIGN exacto, MFA y actor autorizado; no autodelegación ilimitada ni conceder operaciones fuera de la matriz aprobada. Retiro conserva grant inactivo/historial en vez de borrar atribución. Un grant activo no basta si el rol/membresía ya no permite operación.

Cambiar responsabilidad sin tocar grants deja acceso previo vigente hasta revocación explícita: mostrarlo en confirmación #36, no afirmar que reasignar retiró acceso. El owner puede seguir con stewardship exacto del expediente aun si otro es principal. Si se desea un comando conjunto de reasignación+revocación, debe declarar los dos efectos, comprobar todos sus targets y registrar un hecho compuesto tipado en la misma transacción; no usar listener asíncrono que deje una ventana de acceso.

| Escenario | Efecto exigido / propuesta pendiente |
|---|---|
| Miembro SUSPENDED | Autoría/relaciones/grants se conservan; autorización ACTIVE falla. No reasignar ni dar acceso al sustituto automáticamente |
| Miembro REVOKED | Terminal por #33, no reactivar ni invitar para eludirlo; historial permanece. Gestión explícita de cartera pendiente #36 |
| Reactivación SUSPENDED | V11 permite bajo cuota de usuarios; como grants previos pueden volver a operar al estar ACTIVE, D74-03 propone conservarlos con revisión explícita previa del owner. No afirmar que V11 ya limpia grants; alternativa requiere política nueva de suspensión de cartera |
| Cambio de rol | Se reevalúa rol/capacidad en cada operación. Subir a supervisor no crea grants; bajar a auxiliar hace fallar la matriz propuesta. Grants conservados podrían volver a habilitarse al regresar a un rol permitido: decisión explícita de conservación/revisión, misma que reactivación |
| Reasignación | Cerrar relación y abrir nueva bajo lock taxpayer; target ACTIVE, identidad ACTIVE y mismo tenant; permisos se gestionan explícitamente |
| Usuario en varios despachos | MembershipId y grants diferentes, incluso mismo userId/RFC; no copiar cartera entre tenants |
| Único integrante | Con D74-03 puede crear y mantener sus expedientes; no se otorga OBLIGATION_APPROVE ni revisión independiente por ser owner. OQ-005 separado |
| Despacho suspendido / usuario global DISABLED | Nuevas operaciones protegidas fallan; no borrar registros. Revalidar en backend y limpiar UI al conocer denegación |
| Operación concurrente con revocación | Gana el orden de locks/commit vigente; no cancelación retroactiva. Autoridad congelada sólo durante ese callback, nunca cacheada para el siguiente |

## Lecturas, RLS y UX

Colección VIEW aplica grants y RLS antes de filtros/count/orden/paginación; sin grants devuelve vacío. Item ajeno/inexistente/no asignado o sin autoridad de item devuelve 404 uniforme según IdentityStore vigente; colección sin capacidad403, sesión inválida401. No exponer por qué falló MFA/asignación de un item. Restore/edición sin autoridad tampoco filtra estado ni versión.

Documento necesita su propio grant y pertenencia de expediente; no pasar de VIEW taxpayer a DOWNLOAD document dentro de un contexto xid inmutable. Diseñar función/puerto específico de documento que verifique ambos alcances sin cache de permisos. Export/jobs reautorizan con política propia; no elevar outbox a actor. frontend usa sólo acciones que backend devuelve para la presentación **como indicios no reutilizables**; el comando siempre reautoriza. #34 no agrega endpoint genérico de consulta de permisos.

Pruebas futuras deben cubrir matriz por fila, dos tenants con mismo RFC sintético, dos contadores con carteras distintas, owner sin grant, rol revocado/MFA/CSRF, colecciones vacías y total no filtrante, archivos y clases sensibles; además SQL runtime sin WHERE, resource_fence/tenant_fence omitidas y grant concurrente que no amplía el conjunto congelado. Cero grants productivos en #74.
