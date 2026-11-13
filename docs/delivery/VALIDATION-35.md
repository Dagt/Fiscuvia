# Validación C02 #35

Base `518bc327173c355b2e6f843442f74248b980ff5c` (main, merge PR #93). Rama `issue-35-taxpayer-directory-web`. PR Draft, sin merge, despliegue, cierre manual o inicio de #36. [Alcance/estados](../ux/TAXPAYER-DIRECTORY-35.md), [contratos](../api/taxpayer-directory-35.md).

La evidencia remota debe corresponder al SHA final del PR: Backend CI, Web CI, OpenAPI CI, Security CI y CI Required. Un resultado local o de SHA anterior no sustituye esos checks. Los resúmenes allowlist de CI registran testedSha/sourceSha. Esta sección se completará con el resultado observado antes de entregar.

## Comandos reproducibles

Desde raíz, con JDK 25, Node 24.13.1/npm 11.8.0 y Docker Linux disponible:

```powershell
npm ci --prefix tools/ci
npm ci --prefix web
cd backend
.\mvnw.cmd -B -ntp clean verify
cd ..
npm run typecheck --prefix web
npm run lint --prefix web
npm run format:check --prefix web
npm test --prefix web -- --reporter=default --reporter=json --outputFile=../.ci-work/vitest.json
npm run build --prefix web
npm run test:e2e --prefix web -- --reporter=line,json
node tools/ci/auth-browser.mjs
npm run openapi --prefix tools/ci
npm test --prefix tools/ci
npm run security --prefix tools/ci
```

HTTP real validado contra OpenAPI: `node tools/ci/contracts.mjs --taxpayers backend/target/taxpayer-contract-responses.json`; los ocho métodos/rutas fiscales requieren evidencia real, incluidos ambos GET de presentación. Backend incluye V13 limpia/upgrade/rollback/checksums, permisos, grants obsoletos, MFA, cuota oculta sin filtración, filtro antes de límite, RLS, aislamiento y guard de privilegios.

Captura pública reproducible, tras commit de fuentes: `$env:FISCUVIA_TAXPAYER_CAPTURE='1'; node tools/ci/auth-browser.mjs`. Las suites anteriores y las cuatro nuevas recorren la aplicación real. Capturas verificadas con axe y sin overflow documentan escritorio/móvil; 1280×800, 768×1024 y 320 px se comprueban en el recorrido. Fuente, hashes de árboles, navegador y SHA-256 en [manifiesto](../ux/evidence/issue-35/capture-manifest.json). No se publican credenciales, sesión, configuración del harness, cuerpos privados o logs completos.

Mínimos anteriores conservados: Java 522, Vitest 132, Playwright base 61, OIDC 20, tools/ci 26. Se añaden pruebas de #35; los gates se incrementan para exigirlas, sin bajar mínimos ni omitir suites. Cambios de fallback 404 en pruebas apuntan ahora a una ruta realmente inexistente, ya que `/app/taxpayers` pasa a ser módulo real.

## Límites y riesgos de revisión

- V13 sólo funciones de lectura y helper privado; V1–V12 intactas, sin nuevas tablas ni grants productivos.
- Responsable y ficha 360° siguen diferidos a #36. No se aprueban reglas fiscales, obligaciones, SAT, documentos u honorarios.
- Señales temporales, reautorización en cada comando. Capacidad no reservada en GET; cambio concurrente puede rechazar un comando.
- Offset no garantiza snapshot entre páginas. Se emplea desempate estable por UUID y se actualiza tras mutación.
- Recuperación idempotente en memoria del panel; cambiar contexto o recargar descarta material privado. No existe almacenamiento fiscal local ni recuperación persistente entre sesiones.
- Referencia escrita de Clientes aprobada en #52; collage original ausente. Comparación con criterios y componentes existentes, sin afirmar pixel-diff o certificación WCAG.
