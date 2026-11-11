# Validación #32 — integración productiva V9

Base revisada del PR #90: `cb2b37d1f0a8c3fa5a80b8371c7f4b1c4d9009dc`. [Aprobación expresa de Dagt](https://github.com/Dagt/Fiscuvia/pull/90#issuecomment-6798951955). Trabajo exclusivamente en el mismo Draft/rama; sin merge, cierre de #32, despliegue ni avance a #33.

V1–V8 intactas. V9 instala TENANT_PLAN_VIEW sólo para owner ACTIVE, tenant ACTIVE y MFA confiable ≤10 minutos, con la referencia exacta de aprobación. Instala entitlement_capacity BEFORE INSERT OR UPDATE y limita la excepción sin configuración al primer owner aprobado mediante bootstrap migrador sin actor. No toca ni invalida membresías históricas.

Aceptación real: IdentityStore.accept conserva su llamada original y los controles OIDC/destinatario/token/estado; el INSERT alcanza el trigger productivo y confirma la reserva junto con la membresía/journal/outbox. P3201/P3202 se traducen a QUOTA_UNAVAILABLE 409 en español. Las reactivaciones UPDATE quedan protegidas y auditadas; no se añade una API pública de equipo ni se amplían permisos de #33.

Pruebas nuevas y conservadas: EntitlementTests exige 39 casos, usando V1–V9 reales sin instalar permisos/triggers positivos. Incluye dos aceptaciones HTTP concurrentes por última plaza, confirmación sin doble conteo, invitación pendiente sin consumo, cuotas ausentes/vencidas/cero, reducción no destructiva, reactivación con/sin cupo, identidad DISABLED, roles, MFA ausente/vencido sobre sesión OIDC, owner inactivo, tenant suspendido, fallos independientes de journal/outbox, replay, revocación concurrente y la misma conexión física reutilizada entre tenants. Los tests negativos alteran/restauran trigger/política para demostrar que la guarda detecta pérdida de protección. Los tests de motor previos conservan su adaptador SQL confiable; no sustituyen los positivos OIDC de autorización.

MembershipCapacityMigrationIT agrega upgrade V8 con una membresía histórica ACTIVE sin configuración, checksums previos, instalación única, checksum alterado y rollback/reintento. La creación limpia V1–V9 se verifica en los contextos/JAR. Las suites anteriores reciben límites exclusivamente sintéticos explícitos para continuar sus escenarios; no grants, triggers temporales ni excepciones productivas para tests.

Mínimos exigidos: 385 Java (347 Surefire +38 Failsafe), 92 Vitest, 61 Playwright de fundación/presentación, 15 Playwright OIDC y 19 tools/ci, conservando 363/91/61/13/19. Dos nuevos OIDC de navegador consultan Plan y uso real, aceptan una invitación que llena el cupo, deniegan al contador y comprueban cambio de contexto; capturas opcionales sólo de Fiscuvia, nunca del proveedor. Los fixtures de límite son sintéticos; los permisos y triggers proceden exclusivamente de V9.

## Ejecución y evidencia

Java Temurin 25.0.5+8 / Maven wrapper 3.9.16; Node 24.22.1/npm 11.9.0 dentro del rango del proyecto; PostgreSQL 18.6 y Keycloak 26.8.1 fijados por digest. Se usa proxy/CA del entorno fuera de Git, sin desactivar TLS. Docker rootless local, umask 0022 para fuentes públicas y scripts sintéticos; logs/handoff/secretos permanecen privados. Trivy conserva cap-drop/no-new-privileges, fuentes read-only, dev/test y caché de entorno en memoria.

Comandos: Maven clean verify; npm ci web/tools; typecheck, ESLint, Prettier, Vitest, build, Playwright completo y auth-browser; OpenAPI implementado/borrador, generación TS y conformidad HTTP; Gitleaks, actionlint, zizmor, CycloneDX con test + Trivy con dev/test; reports/allowlist sanitizados y git diff --check. Resultados efectivos locales y los cinco checks del nuevo SHA se registran en [PR #90](https://github.com/Dagt/Fiscuvia/pull/90). CI #44 del SHA anterior no acredita V9.

La evidencia vigente por SHA y URL del run está en el PR #90; sólo se declara éxito remoto después de observar Backend CI, Web CI, OpenAPI CI, Security CI y CI Required correctos. La descripción del Draft mantiene la evidencia vigente por SHA/run sin autorreferencias al commit.

## Límites vigentes

Sin precios, cobros, TENANT_PLAN_MANAGE, administración completa de equipo ni productores de consumo para contribuyentes, almacenamiento o jobs. Esos recursos permanecen NOT_INTEGRATED y sin medición; outbox no es un job de negocio. Las reservas persistentes requieren liberación explícita, sin worker de expiración nuevo. OQ-007/014/015, ADR-015 completa y #74 permanecen pendientes. [Modelo y locks](../docs/architecture/TENANT-ENTITLEMENTS.md), [capturas/procedencia](../docs/ux/evidence/issue-32/README.md).

Durante la validación local una recompilación concurrente del harness invalidó dos clases de pruebas; se corrigió la orquestación para ejecutar Maven y el harness de forma secuencial y repetir la regresión completa. Una interrupción del entorno terminó después los procesos; esas ejecuciones parciales no se presentan como resultados correctos. Se conservan los logs privados para diagnóstico.
