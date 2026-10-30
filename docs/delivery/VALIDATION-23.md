# Evidencia de CI #23

PR Draft [#78](https://github.com/Dagt/Fiscuvia/pull/78), rama `issue-23-github-actions-ci-foundation`, base main `bf61a9697e606cfd6b709a09ed2997947c8c268f`. Los comandos, versiones, permisos, artifacts y recuperación se describen en [CI.md](CI.md). La descripción del PR registra el SHA final y la última ejecución; las cifras de esta evidencia no sustituyen consultar ese SHA.

## Validación local

| Comprobación | Resultado observado |
|---|---|
| Windows: Maven wrapper clean verify | **47 aprobadas**: 37 Surefire + 10 Failsafe, cero fallos/errores/omitidos |
| PostgreSQL real / Flyway / Modulith / JAR | Incluidos en verify: PG 18.6, historial, reejecución, checksum alterado, rollback SQL, roles y errores de arranque; Ryuk activo |
| Windows: npm ci / typecheck / lint / format:check / build | Aprobados |
| Windows: Vitest / Playwright Chromium | **13 + 13 aprobadas**, cinco proyectos Playwright, cero omitidos/flaky/reintentos |
| Linux: npm ci / typecheck / lint / format:check / build | Aprobados en Node 24.13.1 bookworm; npm 11.8.0 |
| Linux: Vitest / Playwright Chromium | **13 + 13 aprobadas**, instalación real `playwright install --with-deps chromium`, Chromium 156.0.8078.4 (revisión 1248) |
| Gates de CI / OpenAPI, Windows y Linux | **4 pruebas aprobadas** en cada entorno, incluyendo rechazo de YAML/estructura/refs inválidos; borrador actual válido estructuralmente |
| Gitleaks | Historial completo y snapshot de fuentes sin hallazgos; artifacts depurados sin hallazgos |
| actionlint / zizmor offline | Sin hallazgos; no se excluye ninguna regla |
| Trivy lockfiles npm incluyendo dev y SBOM Maven incluyendo test | **0 HIGH/CRITICAL** detectadas; SBOM de 120 componentes; sin exclusiones |
| git diff --check | Aprobado |

Windows: Oracle JDK 25.0.3+9, Maven 3.9.16, Docker Linux 29.2.0. Backend sin modificaciones funcionales ni reducción de cobertura. Scripts/reportes validan resultados de ambos motores web y todas las suites backend actuales. Los datos/credenciales de DB son efímeros sintéticos; no se creó `.env` para los tests.

Los comandos de backend y web de CI son los existentes del repositorio. No se cambian tests, timeout/retries Playwright ni umbrales de accesibilidad. Las capturas generadas por las pruebas son de la app real, pero #23 no cambia UI ni crea baselines #56; no se incorporan diferencias Linux como evidencia visual aprobada.

### Límite de la comprobación adicional backend Linux

Se intentó `clean verify` con Temurin **25.0.5+8** en Ubuntu noble, usando el daemon de Docker Desktop desde otro contenedor y host networking. Compiló código y tests; las 15 pruebas de configuración y 3 de límites Modulith aprobaron. Las dos clases dependientes de DB fallaron antes de probar PostgreSQL: **Could not connect to Ryuk at 127.0.0.1:50743**. Surefire informó 20 ejecuciones, 2 errores y 0 omitidas; Failsafe no llegó a ejecutarse. **No es una validación backend Linux aprobada.** El entorno Docker anidado no equivale al JVM sobre el host Ubuntu del workflow; hace falta comprobar ese runner cuando se resuelva el bloqueo remoto. No se desactivó Ryuk ni se modificaron guards para sortear el fallo.

El primer contenedor mínimo carecía de unzip y el wrapper intentó tar.gz frente al checksum fijado del ZIP. Se instaló unzip en el contenedor de prueba y se añadió su precondición explícita en los jobs Maven; checksum y wrapper se conservaron. El runner Ubuntu 24.04 hospedado lo incluye. Los archivos fuente para las comprobaciones Linux se copiaron desde `git archive` del commit `d7f5de6`, sin archivos ignorados ni `.env` local.

Imágenes adicionales usadas **sólo para la comprobación local**, sin introducirlas en el runtime ni en el workflow:

- `eclipse-temurin:25.0.5_8-jdk-noble@sha256:0939ed943f8107b943b6a6ac2e21d24568c24e4443d496e4d8b8e8da1ccdde1c`.
- `node:24.13.1-bookworm@sha256:00e9195ebd49985a6da8921f419978d85dfe354589755192dc090425ce4da2f7`.

## GitHub real: workflow detectado, ejecución bloqueada

La API devuelve workflow **401305416**, nombre `CI`, ruta `.github/workflows/ci.yml`, estado `active`. Actions está habilitado (`enabled: true`, `allowed_actions: all`). El evento PR Draft creó [ejecución #1 / 40991908332](https://github.com/Dagt/Fiscuvia/actions/runs/40991908332) para `d7f5de6fadb49c3c78d54fe57e57314d13987e1a`.

| Check | Job ID inicial | Estado / pasos |
|---|---|---|
| Security CI | 123856073659 | failure / 0 |
| Web CI | 123856073847 | failure / 0 |
| OpenAPI CI | 123856073879 | failure / 0 |
| Backend CI | 123856074041 | failure / 0 |
| CI Required | 123856091554 | failure / 0 |

Los cinco checks tienen la misma anotación de fallo (REST `check-runs/{id}/annotations`, HTTP 200):

> The job was not started because recent account payments have failed or your spending limit needs to be increased.

Sin runner asignado, sin pasos, sin artifacts. Obtener logs devolvió 404 BlobNotFound. Esta evidencia respalda un bloqueo de la cuenta relacionado con pagos/límite; no determina cuál de esas dos condiciones existe. **REMOTE CI NOT VERIFIED**, ningún build/test remoto aprobado. La ejecución del SHA final y los resultados actuales se enlazan en el PR.

El titular debe revisar Billing & licensing / Billing & plans, pagos y presupuesto/límite de **Actions**, corregir la condición de su cuenta y después usar **Re-run all jobs** sobre el SHA final. Si la cuenta aparece correcta, contactar GitHub Support con run/anotación. No se cambia billing ni se autoriza gasto desde esta entrega; no se evaden restricciones usando otro repositorio o runner externo. Ver [instrucciones y guía oficial](CI.md#bloqueo-remoto-comprobado-durante-23).

Protección/rulesets: el conector no tiene permiso de lectura (`403 Resource not accessible by integration`). Con autenticación existente de Git, GET de protección y rulesets devolvieron **403: Upgrade to GitHub Pro or make this repository public to enable this feature**. El owner necesita decidir un plan compatible para aplicar protección en este repositorio privado. No se cambian reglas, visibilidad, plan ni auto-merge.

## Alcance pendiente

Reejecución real de los cinco checks y revisión de artifacts por SHA cuando GitHub habilite runner; backend en Ubuntu nativo aún no acreditado. Main protection requiere decisión del owner sobre el plan y configuración posterior autorizada. OpenAPI conserva semántica preliminar allOf/additionalProperties para #24; no se implementa contrato de producción. No cambios de dominio, migraciones, RLS, OIDC, módulos ni datos fiscales. #52/#56 independientes. PR permanece Draft sin merge ni cierre de issues.
