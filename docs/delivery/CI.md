# CI reproducible — issue #23

## Política temporal vigente — #79

PR #78 integrado en main `edbc6ddc1a54e17e5cd38dd108af67cc5eb7e1c7`. El propietario decidió mantener presupuesto Actions **$0** y pruebas locales completas. **REMOTE CI NOT VERIFIED — GitHub Actions bloqueado por presupuesto/facturación; seguimiento #79.** No cambiar billing, deshabilitar workflows, reintentar runners ni asumir reinicio de cuota en una fecha concreta. #79 registra la comprobación remota futura; los fallos pre-runner no son fallos de código ni CI aprobada. El diagnóstico de #23 abajo es histórico, no una instrucción vigente para aumentar el presupuesto.

#24 añade conformidad de respuestas HTTP reales y tipos generados desde `docs/api/openapi-v1.yaml`, manteniendo el borrador propuesto separado. Después de Maven verify ejecutar `node tools/ci/contracts.mjs --responses backend/target/contract-responses.json`; Backend CI incluye ese paso. `npm run openapi --prefix tools/ci` valida ambos documentos y la generación exacta. Versiones nuevas: openapi-typescript 7.13.0, AJV 8.20.0 / ajv-formats 3.0.1, Prettier 3.9.10 (formato del generado), con lockfile.

Trazabilidad: #23, épica #3, NFR-040/044, dirección ADR-013 (continúa Proposed). Base auditada: main `bf61a9697e606cfd6b709a09ed2997947c8c268f`, PR #75–77 integrados. No endpoints #24, cambios de negocio, migraciones nuevas, RLS ni despliegues.

## Workflow y checks estables

`.github/workflows/ci.yml`, nombre `CI`, Ubuntu **24.04**. PR hacia main (también Draft), push main y `workflow_dispatch`. Sin filtros de rutas, secretos ni `pull_request_target`. Concurrencia por workflow/ref: cancela sólo PR obsoletos; main se serializa. Timeout por job, runner efímero hospedado por GitHub; nunca un runner propio con datos/credenciales de producción para ejecutar código de PR.

| Check | Alcance | Timeout |
|---|---|---|
| `Backend CI` | Wrapper clean verify, JUnit/Spring Modulith, PostgreSQL/Flyway y JAR real, verificación de reportes | 20 min |
| `Web CI` | npm ci, TS, ESLint, Prettier, Vitest, build, Chromium/Playwright completo | 15 min |
| `OpenAPI CI` | Tests negativos de los gates, sintaxis/estructura/refs del borrador | 10 min |
| `Security CI` | Secretos/historial, archivos sensibles, workflows, vulnerabilidades npm/Maven | 20 min |
| `CI Required` | `always()`, exige los cuatro resultados `success`; skipped/cancelled/failure no pasan | 2 min |

Permisos globales sólo `contents: read`; checkout sin persistir credenciales. Acciones oficiales fijadas por SHA completo, con versión en comentario: checkout v7, setup-java v6, setup-node v7, upload-artifact v6. Actualizaciones de SHA/digest/lockfile requieren PR revisable y revalidación; no se descargan scripts `curl | sh`.

En PR se prueba el **merge SHA de GitHub**, no sólo el head aislado: los resúmenes registran `git rev-parse HEAD` y `SOURCE_SHA` (head del PR). En push/manual coinciden. Los nombres de artifacts incluyen `github.sha`, son inmutables por ejecución y duran **7 días**. Consultar siempre la ejecución correspondiente al SHA final; un check de un SHA anterior no acredita el actual.

## Herramientas fijadas

| Herramienta | Versión |
|---|---|
| JDK en CI | Eclipse Temurin **25.0.5+8** (Java 25 LTS); local histórico Oracle 25.0.3+9 |
| Maven / Wrapper | **3.9.16 / 3.3.4**, checksum del wrapper en el repositorio |
| Boot / Modulith / JUnit | **4.1.2 / 2.1.2 / 6.0.3**, sin cambios |
| PostgreSQL / Flyway / Testcontainers | **18.6 / 12.4.0 / 2.0.5**, imagen PG con digest en infra y tests |
| Node / npm | **24.13.1 / 11.8.0**, `.nvmrc`, engines y lockfiles |
| Playwright / Chromium | **1.64.1**; revisión de navegador resuelta por ese paquete, `--with-deps chromium` |
| Redocly CLI / YAML / fast-xml-parser | **2.72.1 / 2.9.1 / 5.11.2**, lock en `tools/ci/` |
| CycloneDX Maven plugin | **2.9.3**, SBOM local incluyendo test scope |
| Gitleaks / actionlint / zizmor / Trivy | **8.30.1 / 1.7.12 / 1.31.1 / 0.76.1**, imágenes con SHA256 en `tools/ci/security.mjs` |

Java/npm verifican versiones efectivas; el runner registra ImageOS/ImageVersion, Docker y Maven. Node 24.13.1 ya distribuye npm 11.8.0: no se reinstala globalmente fuera de lockfile. Ubuntu 24.04 fija la familia, pero GitHub mantiene su imagen y paquetes de sistema; no es una imagen de runner bit a bit congelada. Las bases públicas de vulnerabilidades se actualizan por diseño: una ejecución posterior puede detectar avisos nuevos con el mismo lockfile. Trivy usa DB descargada y análisis local (`--offline-scan`, telemetría desactivada); no envía código ni SBOM a un servicio de análisis. No usa trivy-action ni tags flotantes de imágenes.

## Ejecución local equivalente

Requiere JDK 25, Node/npm exactos y Docker Linux iniciado, salida HTTPS a Maven Central, npm, registros de imágenes/DB públicas y distribuciones Chromium. En Linux mínimo instalar `unzip`: el wrapper fija el checksum del ZIP de Maven; sin unzip su fallback tar.gz no coincide con ese checksum. Ubuntu 24.04 de GitHub ya incluye unzip. Conservar la verificación, no quitar el checksum. No requiere `infra/.env`, Compose, un servicio PostgreSQL permanente, tokens GitHub ni datos fiscales. Docker debe permitir contenedores/redes/puertos y Ryuk; sin reuse ni `TESTCONTAINERS_RYUK_DISABLED=true`. No desactivar checks si Docker falta.

Desde la raíz, POSIX:

```sh
node tools/ci/identity.mjs
npm ci --prefix tools/ci --ignore-scripts
backend/mvnw -f backend/pom.xml -B -ntp clean verify
node tools/ci/contracts.mjs --responses backend/target/contract-responses.json
node tools/ci/reports.mjs backend
backend/mvnw -f backend/pom.xml -B -ntp org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom -DincludeTestScope=true -DoutputFormat=json
npm test --prefix tools/ci
npm run openapi --prefix tools/ci
npm run security --prefix tools/ci
mkdir -p .ci-work
cd web
npm ci
npm run typecheck
npm run lint
npm run format:check
npm test -- --reporter=default --reporter=json --outputFile=../.ci-work/vitest.json
npm run build
npx --no-install playwright install --with-deps chromium
PLAYWRIGHT_JSON_OUTPUT_FILE=../.ci-work/playwright.json npm run test:e2e -- --reporter=line,json
cd ..
node tools/ci/reports.mjs web
node tools/ci/artifacts.mjs backend
node tools/ci/artifacts.mjs web
node tools/ci/artifacts.mjs security
git diff --check
```

PowerShell: usar `backend/mvnw.cmd` en lugar de `backend/mvnw`; `New-Item -ItemType Directory -Force .ci-work`; antes de Playwright, `$env:PLAYWRIGHT_JSON_OUTPUT_FILE='../.ci-work/playwright.json'`. Windows instala `npx --no-install playwright install chromium` sin paquetes Linux `--with-deps`. El resto de comandos Node/npm/Docker es igual. Comprobar `$LASTEXITCODE` después de cada comando nativo y detenerse ante fallo. Los scripts Node son portables; los escáneres usan contenedores Linux con digest fijo en ambos sistemas.

Los tests Playwright regeneran capturas históricas de #21. No confirmar diferencias de píxeles generadas por el runner como baselines aprobados: revisar/restaurar sólo esas capturas si no hay cambio UI intencional. Esta entrega no introduce regresión visual #56.

## Integridad y artifacts

Maven usa `clean verify`; la cache sólo guarda descargas `.m2`, no target. npm usa `npm ci`; la cache guarda descargas, no node_modules/dist. No se cachean reportes ni bases con credenciales. Desde #24, `reports.mjs` exige ocho suites backend (mínimos 80 Surefire + 10 Failsafe), 21 Vitest y 13 Playwright, cero fallos/omitidos/flaky/reintentos. Reducciones intencionales requieren revisar esos mínimos, sin flags skip. La política busca también anotaciones Disabled/Ignore y tests skip/todo/only. Sus pruebas negativas demuestran rechazo de omisiones, suites ausentes y resultados incompletos.

Sólo se suben archivos permitidos: backend `summary.json` + `junit.xml` depurado, web y seguridad `summary.json`. Se escanean antes de subirlos. JUnit publicado omite propiedades JVM/env, stdout/stderr y cuerpos de excepciones. No se suben `.env`, archivos SAT, certificados, SBOM, JAR, dumps, reportes crudos, trazas, HTML ni capturas. Si falla una suite, Maven/Playwright conservan el fallo en el job; el verificador vuelve a fallar y **no publica artifacts crudos para diagnosticarlo**. Examinar logs en la ejecución privada, sin copiarlos a servicios externos. Las contraseñas de Testcontainers son UUID temporales; las pruebas existentes verifican su ausencia en logs.

Gitleaks analiza historial completo y snapshot actual de archivos versionados/nuevos no ignorados; nunca copia el `.env` ignorado. El inventario rechaza `.env` (salvo el ejemplo), llaves, certificados, bases/backups/XML/PDF no autorizados. Esto es prevención automática, no una garantía de detectar cualquier dato fiscal posible: el review humano debe mantener el veto a datos reales. No hay exclusiones globales de secretos. Trivy bloquea HIGH/CRITICAL, incluye dev npm y test Maven, sin ignorar unfixed. Otros niveles no son un bloqueo en esta primera política y requieren seguimiento. actionlint valida YAML/expresiones/shell; zizmor offline revisa riesgos sin token, sin consultar advisories de acciones mediante API. Las referencias externas OpenAPI están prohibidas para evitar lecturas arbitrarias o descargas; fixtures negativos comprueban refs rotas/estructura/YAML.

## Estado del borrador OpenAPI y discrepancias auditadas

`docs/api/openapi-v1-draft.yaml` no representa endpoints implementados. #24 mantiene ese documento propuesto, con su composición allOf/additionalProperties pendiente de revisión del modelo, y añade `docs/api/openapi-v1.yaml` para el endpoint público real/infraestructura. Sólo el implementado genera tipos/metadatos; se verifican respuestas HTTP reales sin crear endpoints fiscales ni exigir APIs futuras.

Antes de #23 no había workflows. README/AGENTS/backend/infra aún describían #22 como pendiente de merge: se actualiza al squash #77 confirmado. TEST-STRATEGY mezclaba checks futuros RLS/build Docker con entregas actuales: se distingue explícitamente. Los enlaces del issue apuntan a la rama documental histórica; se usa main actual. TrailerSaaS aporta jobs separados/gate/evidencia, pero su condición para omitir builds Draft, Flutter y contratos generados no se copian. ADR-013 y decisiones fiscales conservan su estado.

## Diagnóstico remoto y protección recomendada

Después del push y PR Draft comprobar Actions, workflow detectado, event/commit, cada job y logs. Si no hay ejecución, consultar la respuesta concreta de GitHub: permisos/políticas, aprobación de fork, error YAML o límite de cuenta; no inferir billing. Registrar **REMOTE CI NOT VERIFIED** cuando no se ejecutó y repetir localmente los comandos equivalentes. Reintentar sobre el SHA final sólo después de corregir la causa; no usar otro repositorio/runner externo para evadir restricciones.

- Docker: comprobar `docker info` Linux; Ryuk y socket accesibles. No sustituir con H2, mocks o skip.
- Linux/Chromium: ejecutar `install --with-deps chromium` y revisar el error real; no relajar accesibilidad, tamaños ni tests.
- Escáneres/DB: comprobar acceso HTTPS a registros. Un fallo de descarga es fallo de CI, no resultado limpio. Actualizar lock/digest si hay vulnerabilidad; excepciones sólo específicas, justificadas y revisadas.
- Versiones/lock: respetar engines/.npmrc; `npm ci` falla ante lock desincronizado. No instalar dependencias flotantes para ocultarlo.
- Artifacts: un fallo de sanitización/scan impide subida; no ampliar a `target/**` o toda la raíz.
- `workflow_dispatch`: GitHub sólo lista el botón cuando el workflow existe en la rama default; los PRs pueden disparar el workflow nuevo antes del merge. No habilitar auto-merge para resolverlo.

No se cambian protecciones. Recomendación para main: exigir los checks exactos `Backend CI`, `Web CI`, `OpenAPI CI`, `Security CI` y `CI Required` de GitHub Actions, rama actualizada, al menos una aprobación humana, descartar aprobaciones obsoletas y revisión de cambios de workflows por responsables. Prohibir merges con checks fallidos, bypass rutinario, force-push y eliminación; el owner decide excepciones administrativas. La consulta con el conector devolvió **403 Resource not accessible by integration**. Una consulta posterior autenticada de sólo lectura a protección y rulesets devolvió **403 Upgrade to GitHub Pro or make this repository public to enable this feature**: el plan actual no permite esa función en este repositorio privado. El owner debe decidir un plan compatible para poder aplicar estas recomendaciones. No se cambió plan, visibilidad ni reglas.

### Bloqueo remoto comprobado durante #23

El workflow **401305416** está `active`; Actions está habilitado (`enabled: true`, `allowed_actions: all`). El PR Draft [#78](https://github.com/Dagt/Fiscuvia/pull/78) creó [CI #1](https://github.com/Dagt/Fiscuvia/actions/runs/40991908332) para `d7f5de6fadb49c3c78d54fe57e57314d13987e1a`. Los cinco jobs fallaron antes de asignar runner, con cero pasos y cero artifacts. Sus anotaciones REST (`check-runs/{jobId}/annotations`, HTTP 200) indican pagos recientes fallidos **o** necesidad de aumentar el límite de gasto; la API no distingue cuál de esas condiciones aplica. Los logs no existen (404 BlobNotFound), consistente con que no arrancaron.

**REMOTE CI NOT VERIFIED.** Se verificó que GitHub reconoce/dispara el YAML; no se ejecutó ningún build ni test remoto. Este fallo no acredita compatibilidad del runner ni éxito de checks. La inspección final por SHA y su enlace quedan en el PR, sin reintentos repetidos mientras siga el mismo bloqueo.

Acción humana: el titular de Dagt debe abrir Settings → Billing & licensing (el aviso de GitHub dice Billing & plans), revisar pagos y el presupuesto/límite específico de **Actions**, y corregir la condición que muestre su cuenta; no se autoriza ni realiza un gasto automáticamente. Ver [guía oficial de presupuestos](https://docs.github.com/en/billing/how-tos/set-up-budgets). Si pagos y límite aparecen correctos, contactar GitHub Support con el run y la anotación. Después usar **Re-run all jobs** en la ejecución del SHA final y verificar los cinco checks y artifacts. No hace falta sacar el PR de Draft, publicarlo, cambiar protecciones ni crear secrets. La restricción del plan para protección de ramas es un asunto distinto del bloqueo de ejecución.

La [evidencia local y el primer bloqueo remoto](VALIDATION-23.md) está registrada; SHA final y última ejecución se adjuntan al PR de #23. Nunca interpretar este documento o la existencia de checks como prueba de éxito remoto.
