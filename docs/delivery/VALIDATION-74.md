# SPEC01 #74 — protocolo y evidencia de validación

**Entrega documental con aprobación limitada D74-01..05, 2026-11-12.** [Índice y alcance](../architecture/SPEC01-fiscal-domain-74.md). Base main `05dc33e77e290777c316d26edabb7ce1e7b28c44`, merge PR #91 (#33), padre `19d6ec043ccbfda3c75c39b3e5428a72666cd3ba`. Rama `issue-74-fiscal-domain-specification`. El SHA final y ejecución CI se registran en el PR Draft; esta referencia evita atribuir un resultado a un commit que todavía no existía al escribir el archivo.

## Checks locales aplicables

Windows/PowerShell, Node24.13.1 y npm11.8.0 del proyecto. Se ejecutan las herramientas existentes sin cambiar dependencias, workflows, mínimos, presupuesto o permisos. Comandos desde raíz:

```powershell
npm test --prefix tools/ci
npm run openapi --prefix tools/ci
node tools/ci/openapi.mjs docs/api/openapi-taxpayers-34-proposed.yaml
git diff --check
git diff 05dc33e77e290777c316d26edabb7ce1e7b28c44 -- backend/src/main/resources/db/migration
git diff --name-only 05dc33e77e290777c316d26edabb7ce1e7b28c44
```

Antecedente sobre el HEAD revisado `260c53202a03a80597769f6fab80e348b0e437bd` (no evidencia del nuevo HEAD): tools/ci **23/23 PASS**, cero omitidos/fallidos; validadores de ambos OpenAPI anteriores y generación determinista **PASS**; lint del nuevo OpenAPI aislado **PASS**. Los mensajes de fixtures inválidos son oráculos negativos de la suite, no fallos de la entrega. Tras fijar HEAD se repiten checks aplicables y se publican resultados ligados a ese SHA en PR.

Antecedente de ese HEAD: **203 enlaces Markdown relativos**, cero destinos ausentes; **10 schemas** del contrato propuesto compilados con AJV2020 estricto y **13 ejemplos** sintéticos positivos/negativos de DTO cerrado/versión/fechas, cero discrepancias; referencias internas resueltas. **11 blobs de migración idénticos** a la base; 21 archivos afectados, todos Markdown/documentación u OpenAPI Proposed. Inspección cruzada de D74/matriz/schema/API/estados y aislamiento. No son pruebas de ejecución de dominio ni verificaciones SAT. Las cantidades/resultados finales se publican en PR, sin instalar un test fiscal productivo ni cambiar el gate de CI para una propuesta.

No hay pruebas backend/web nuevas ni cambio ejecutable: no se requiere repetir Maven/browser local por una edición documental. Las suites completas existentes siguen habilitadas y GitHub Actions las ejecuta en el PR Draft; la evidencia nueva debe identificar source SHA y checkout de integración, cinco checks: Backend CI, Web CI, OpenAPI CI, Security CI y CI Required. El nuevo OpenAPI se valida explícitamente en local; el script estándar de CI sólo enumera los dos contratos anteriores, sin afirmar que lo descubre automáticamente. Si CI no inicia por billing, registrar **REMOTE CI NOT VERIFIED** y motivo real, sin aumentar presupuesto ni deshabilitar workflow. Ningún resultado histórico de #33 acredita #74.

## Integridad de alcance

- Cero migraciones fiscales nuevas; V1–V11 se comparan como blobs Git idénticos contra la base, además de inspeccionar sus responsabilidades/constraints. Esto verifica inmutabilidad de archivos, no pretende una nueva ejecución de Flyway en esta entrega.
- Cero cambios de Java/TS/CSS, dependencias, infra o privilegios. OpenAPI implementado y tipos generados permanecen intactos; sólo se enlaza una propuesta independiente desde el borrador histórico.
- Cero datos reales, credenciales, archivos fiscales o screenshots. RFC genéricos mencionados son valores documentados cuya exclusión exacta está aprobada; excepciones pendientes, sin datos de clientes ni consultas al SAT.
- Cero capturas UI nuevas: no hay UI implementada en #74. Los criterios1440×900/390×844, vacío/error y accesibilidad quedan exigidos para los PR con cambios reales de frontend.
- OQ completas siguen Open y ADR globales Proposed; D74-01..05 y diferimientos están aprobados sólo para la especificación inicial de #34, según [fuente del propietario](../decisions/issue-74-owner-approval.md). No se inicia #34 en esta tarea; #38–45/documentos/honorarios conservan validación/aprobación propia.
- PR Draft con Refs #74, sin merge, Ready, despliegue, cierre manual de issues ni mensajes externos fuera del PR solicitado.

## Revisión de consistencia y límites

Se reconciliaron identidad/tenant reales, FK de grants, bootstrap CREATE, RLS y contexto inmutable, cuota con visibilidad completa interna, snapshots privados frente al journal sin PII, RFC duplicado/archivo, régimen múltiple/procedencia, estados ortogonales y versiones de documentos. Se señalan discrepancias de v0.1 en vez de declararlas resueltas. Las fuentes SAT sólo sustentan estructura/consulta separada, no calendario legal ni validación de un RFC real.

El esquema es realizable como propuesta de constraints/funciones, pero **no está probado con DDL fiscal ejecutado**: eso está expresamente fuera de #74. El propietario ya aprobó el corte #34 y diferimientos; el contador aún debe revisar las reglas de módulos posteriores y excepciones. CI verde no amplía ese alcance ni aprueba el resto del dominio fiscal. La especificación #34 incluye clean install/upgrade V11, PostgreSQL/RLS/OIDC reales, carreras y rollback de sinks que deberán acreditar la implementación posterior.

## Incorporación de la aprobación — revisión final de #92

La instrucción recibida de Dagt se registra por ID/fecha/autoridad/alcance/restricciones/dependencias/fuente en issue-74-owner-approval. No hay comentario de aprobación GitHub inventado. Se confirmó origin/main sin avance respecto de `05dc33e77e290777c316d26edabb7ce1e7b28c44`; se conserva rama y PR #92. El SHA final, diff/mergeabilidad y resultados nuevos se documentan en el cuerpo del PR, sin heredar verde de `260c532`.

Checks de esta actualización: tools/ci **23/23 PASS**, ambos OpenAPI anteriores/tipos generados y OpenAPI aislado **PASS**; AJV2020 **10 schemas/13 ejemplos** con anotación x-server-validation registrada (no ejecuta validación de negocio); **222 enlaces relativos válidos**, diferencia documental de22 archivos contra main y **11 blobs Flyway idénticos**; revisión cruzada de aprobación/contratos/permisos. Tras fijar commit, resultados/CI del SHA final se registran en PR. No tests/migraciones productivas nuevas, no cambio de mínimos/workflow/lockfiles.

La revisión técnica inspeccionó V11: suspensión/reactivación actualizan membresía y el trigger team_version avanza versión; SET_ROLE actualiza membresía antes de membership_roles. El diseño usa esa versión monotónica para grant_reviews, compara última decisión/version/rol tanto en authorize_operation como resource_allowed y no habilita por replay. Los escenarios y oráculos PostgreSQL/HTTP de no reactivación quedan exigidos a #34. Esto es análisis de código y especificación, **no prueba ejecutada de un mecanismo fiscal inexistente**.

Riesgos residuales: validar DDL/carreras de autorización y cuota en la implementación; impedir futuros productores de rol que omitan invalidar versión; grant obsoleto queda DENY hasta revisión #36 y no hay recuperación global/automática; retención/privacidad y proveedor/MFA productivos conservan gates. No bloquea volver a decidir lo ya aprobado: tras revisión final e integración humana de #92, el siguiente trabajo será #34 desde latest main.
