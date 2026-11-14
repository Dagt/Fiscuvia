# Validación y estado de aceptación — C03 #36

Base exacta `05f1d0b58783438e193ae9ad86e7181b0e62c135` (PR #94); rama `issue-36-taxpayer-360-portfolio`. Sin merge, despliegue, cierre de issue ni inicio #37/38. La autorización de #74 se conserva; [nuevas decisiones](../decisions/issue-36-approval-proposal.md) permanecen pendientes de respuesta expresa. La aprobación de este PR no debe confundirse con conceder esas políticas.

## Alcance comprobable

| Criterio de #36 | Estado de implementación |
|---|---|
| 1 Ruta real de ficha | Implementada `/app/taxpayers/:taxpayerId` |
| 2 Integración con directorio | Implementada dentro del router/shell/contexto existentes |
| 3 Diseño consistente | Implementado sobre #52 y S04 escrita; revisión/evidencia abajo |
| 4 Información administrativa real | Perfil, regímenes manuales y fechas C01; edición/archivo/restauración reutilizados |
| 5 Contactos seguros | **Bloqueado D36-01/02**; modelo/contrato propuestos, sin DDL/API/control falso |
| 6 Responsables autorizados | **Bloqueado D36-01** para proyección/filtro y D36-03 para comandos |
| 7 Metadatos de certificados | **Bloqueado D36-01/02**; sin archivos ni integración SAT |
| 8 Alertas reales | **Bloqueado D36-01/02**, umbral administrativo propuesto 30 días |
| 9 Actividad autorizada | **Bloqueado D36-01**, no journal global ni timeline inventado |
| 10 Permisos por expediente | Reutilizados D74-03, sin nueva delegación/recuperación |
| 11 Aislamiento multitenant | Contexto/cache/cancelación del perfil; contratos y RLS existentes conservados |
| 12 Errores/concurrencia | Implementados en perfil y formulario/comandos compartidos |
| 13 Pruebas completas | Sólo alcance activo y regresión; no se acreditan funciones bloqueadas |
| 14 Capturas reales | Sólo pantallas implementadas; no se simulan escenarios bloqueados |
| 15 Documentación | UX, contratos propuestos, decisiones y matriz de entrega |
| 16 CI aprobado | Registrar los cinco jobs sobre el SHA final en el PR; pendiente hasta ejecución |

## Plan y controles

Conservar todos los mínimos previos: 529 Java (480 Surefire +49 Failsafe), 61 Playwright foundation, 24 OIDC previos y26 gates. La ficha añade12 Vitest (total192) y2 OIDC (total26). No tests omitidos/reintentos ni relajación de RLS/contraste/mínimos. Backend productivo, test fixtures Java y migraciones no se modifican; se ejecuta igualmente clean verify para demostrar regresión PostgreSQL/Flyway/Modulith/Keycloak/autorización/journal/outbox.

Comandos reproducibles desde raíz (Node/JDK e imágenes fijados por repositorio):

```sh
npm ci --prefix tools/ci --ignore-scripts
npm ci --prefix web
npm test --prefix tools/ci
npm run openapi --prefix tools/ci
npm run generate:api --prefix tools/ci
npm run typecheck --prefix web
npm run lint --prefix web
npm run format:check --prefix web
npm test --prefix web -- --reporter=default --reporter=json --outputFile=../.ci-work/vitest.json
npm run build --prefix web
(cd backend && ./mvnw -B -ntp clean verify) # mvnw.cmd en Windows
node tools/ci/reports.mjs backend
node tools/ci/contracts.mjs --responses backend/target/contract-responses.json --taxpayer-responses backend/target/taxpayer-contract-responses.json
PLAYWRIGHT_JSON_OUTPUT_FILE=../.ci-work/playwright.json npm run test:e2e --prefix web -- --reporter=line,json
node tools/ci/auth-browser.mjs
node tools/ci/reports.mjs web
backend/mvnw -f backend/pom.xml -B -ntp org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom -DincludeTestScope=true -DoutputFormat=json
npm run security --prefix tools/ci
node tools/ci/artifacts.mjs backend
node tools/ci/artifacts.mjs web
node tools/ci/artifacts.mjs security
node tools/ux/validate-taxpayer-profile.mjs --current
```

OpenAPI implementado y tipos generados deben quedar sin diff; las propuestas #36 son Markdown aislado, no contrato productivo. Captura pública: `FISCUVIA_PROFILE_CAPTURE=1 node tools/ci/auth-browser.mjs` después de commit del código. El manifiesto identifica fuente, árboles web/backend/fixtures, Chromium, hora, dimensiones y hashes. No se publican configuraciones/credenciales privadas del harness ni reportes crudos con secretos.

## Riesgos y desbloqueo

La entrega es parcial por decisión pendiente, no un módulo 360 completo. Aprobar/rechazar D36-01 (datos legibles) y D36-02 (escrituras administrativas/umbrales/límites) permite implementar las extensiones sin reabrir D74. D36-03 necesita decisión separada sobre quién administra, bootstrap/recuperación, capacidades destinatarias y protección del último administrador; no puede derivarse de ser owner o responsable.

Las referencias de régimen y futuras fechas de certificados son administrativas. No hay validación SAT, calendario legal ni regla fiscal nueva. Un especialista debe validar cualquier interpretación tributaria futura; el umbral propuesto no es un plazo legal. Offset del directorio mantiene sus limitaciones ante cambios concurrentes. La recuperación idempotente actual se conserva mientras viva el contexto; recargar elimina borradores por privacidad.

## Resultados locales y procedencia

- Maven clean verify: **529/529** (480 Surefire +49 Failsafe), cero fallos/errores/omitidos, 20:32min. Worktree aislado `b9765e89dab162f39414dfc5e1514eb984f84e03`, finalizado2026-11-14T01:17:44-06:00. El árbol backend `00ca630aa94ba51085863938f734ce8ab6073e55` y fixtures `9772b5c57204dc5675ae8fa22b134001e0062570` coinciden con la entrega; no se mezclaron target/classes del harness OIDC y clean verify.
- Parser/gates backend y artifacts allowlist:529 y cero omisiones; contratos HTTP:52 generales +175 de contribuyentes, todos válidos. La matriz de regresión conserva sus pendientes históricos; aprobar pruebas actuales no autoriza filas futuras.
- Reporte web en `e9b7d18cfa0863ab9bcfbec472529c78ff6e40c3`:192 Vitest,61 foundation y26 OIDC reales, cero omitidos/fallos/flaky/retries. Los61 foundation se ejecutaron antes de cambios exclusivos del harness de captura; no hubo cambios productivos entre ambas ejecuciones. npm ci/typecheck/ESLint/Prettier/build y26 gates pasaron.
- OpenAPI implementado y generación TypeScript sin diff. Vínculos locales de los Markdown afectados verificados sin roturas; validadores de referencia/#52 y42 imágenes históricas C02 conservados.
- Evidencia C03:22 PNG de fuente `e9b7d18cfa0863ab9bcfbec472529c78ff6e40c3`, árboles coincidentes, Chromium156.0.8078.4; dimensiones/hashes/axe/reflow verificados. [Manifiesto](../ux/evidence/issue-36/capture-manifest.json).
- Gitleaks/Actionlint/Zizmor/Trivy pasaron el gate HIGH/CRITICAL sin exclusiones nuevas. La comprobación remota debe volver a probar el SHA final, incluyendo los commits de evidencia/documentación.

Los cinco resultados CI definitivos se registran en el cuerpo del PR con head SHA y SHA probado del candidato de merge, sin afirmar verde por commits anteriores. Las secciones históricas #34/35 siguen como evidencia de sus fuentes, no como estado de #36. El CI del SHA final es la prueba completa de la entrega final; estas ejecuciones locales tienen la procedencia anterior explícita.
