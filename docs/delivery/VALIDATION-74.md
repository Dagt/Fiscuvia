# SPEC01 #74 — protocolo y evidencia de validación

**Entrega documental Proposed, 2026-11-12.** [Índice y alcance](../architecture/SPEC01-fiscal-domain-74.md). Base main `05dc33e77e290777c316d26edabb7ce1e7b28c44`, merge PR #91 (#33), padre `19d6ec043ccbfda3c75c39b3e5428a72666cd3ba`. Rama `issue-74-fiscal-domain-specification`. El SHA final y ejecución CI se registran en el PR Draft; esta referencia evita atribuir un resultado a un commit que todavía no existía al escribir el archivo.

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

Durante preparación: tools/ci **23/23 PASS**, cero omitidos/fallidos; validadores de ambos OpenAPI anteriores y generación determinista **PASS**; lint del nuevo OpenAPI aislado **PASS**. Los mensajes de fixtures inválidos son oráculos negativos de la suite, no fallos de la entrega. Tras fijar HEAD se repiten checks aplicables y se publican resultados ligados a ese SHA en PR.

Revisión adicional local: **203 enlaces Markdown relativos**, cero destinos ausentes; **10 schemas** del contrato propuesto compilados con AJV2020 estricto y **13 ejemplos** sintéticos positivos/negativos de DTO cerrado/versión/fechas, cero discrepancias; referencias internas resueltas. **11 blobs de migración idénticos** a la base; 21 archivos afectados, todos Markdown/documentación u OpenAPI Proposed. Inspección cruzada de D74/matriz/schema/API/estados y aislamiento. No son pruebas de ejecución de dominio ni verificaciones SAT. Las cantidades/resultados finales se publican en PR, sin instalar un test fiscal productivo ni cambiar el gate de CI para una propuesta.

No hay pruebas backend/web nuevas ni cambio ejecutable: no se requiere repetir Maven/browser local por una edición documental. Las suites completas existentes siguen habilitadas y GitHub Actions las ejecuta en el PR Draft; la evidencia nueva debe identificar source SHA y checkout de integración, cinco checks: Backend CI, Web CI, OpenAPI CI, Security CI y CI Required. El nuevo OpenAPI se valida explícitamente en local; el script estándar de CI sólo enumera los dos contratos anteriores, sin afirmar que lo descubre automáticamente. Si CI no inicia por billing, registrar **REMOTE CI NOT VERIFIED** y motivo real, sin aumentar presupuesto ni deshabilitar workflow. Ningún resultado histórico de #33 acredita #74.

## Integridad de alcance

- Cero migraciones fiscales nuevas; V1–V11 se comparan como blobs Git idénticos contra la base, además de inspeccionar sus responsabilidades/constraints. Esto verifica inmutabilidad de archivos, no pretende una nueva ejecución de Flyway en esta entrega.
- Cero cambios de Java/TS/CSS, dependencias, infra o privilegios. OpenAPI implementado y tipos generados permanecen intactos; sólo se enlaza una propuesta independiente desde el borrador histórico.
- Cero datos reales, credenciales, archivos fiscales o screenshots. RFC genéricos mencionados son casos de identificación a revisar, no datos de clientes ni consultas al SAT.
- Cero capturas UI nuevas: no hay UI implementada en #74. Los criterios1440×900/390×844, vacío/error y accesibilidad quedan exigidos para los PR con cambios reales de frontend.
- Ninguna OQ/ADR cambia a Resolved/Accepted; D74-01..05 pendientes, #34 no se implementa/abre mientras bloqueado. #38–45/documentos/honorarios conservan aprobación propia.
- PR Draft con Refs #74, sin merge, Ready, despliegue, cierre manual de issues ni mensajes externos fuera del PR solicitado.

## Revisión de consistencia y límites

Se reconciliaron identidad/tenant reales, FK de grants, bootstrap CREATE, RLS y contexto inmutable, cuota con visibilidad completa interna, snapshots privados frente al journal sin PII, RFC duplicado/archivo, régimen múltiple/procedencia, estados ortogonales y versiones de documentos. Se señalan discrepancias de v0.1 en vez de declararlas resueltas. Las fuentes SAT sólo sustentan estructura/consulta separada, no calendario legal ni validación de un RFC real.

El esquema es realizable como propuesta de constraints/funciones, pero **no está probado con DDL fiscal ejecutado**: eso está expresamente fuera de #74. El contador/propietario todavía deben revisar decisiones; CI verde de documentación tampoco las aprueba. La especificación #34 incluye clean install/upgrade V11, PostgreSQL/RLS/OIDC reales, carreras y rollback de sinks que deberán acreditar la implementación posterior.
