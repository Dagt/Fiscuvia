# Preguntas abiertas — OQ-### (registro formal)

**Estado:** Open; ninguna respuesta se presume por la mera creación de issues. Cada resolución deberá citar decisión de propietario, experto contable o evidencia documentada y actualizar FR/BR/ADR/API/schema afectados.

## Producto/contabilidad (prioridad MVP)
- **OQ-001 [blocking MVP rules]** Obligaciones prioritarias por tipo de contribuyente y periodicidad: ¿qué plantillas iniciales serán verificadas por experto, y cómo se actualizarán legalmente?
- **OQ-002 [blocking MVP]** ¿Qué estados definitivos de obligaciones y revisión aceptan los despachos (incluido pendiente de presentación, presentada, exenta/no aplicable, por pagar/pagada)?
- **OQ-003 [blocking MVP]** ¿Acuses/pagos son obligatorios para cerrar una obligación, o configurables por tipo? ¿Puede capturarse 'presentada' sin acuse como provisional?
- **OQ-004 [blocking MVP]** ¿Cómo se registra declaración complementaria, corrección, varios pagos o saldo a favor?
- **OQ-005 [blocking MVP]** ¿Qué roles pueden aprobar y reabrir? ¿Se permite autoverificación en despacho unipersonal?
- **OQ-006 [blocking MVP]** ¿Regímenes y obligaciones se crean manualmente, catálogo revisado o CSF parsing posterior?
- **OQ-007 [pricing]** ¿Qué planes/cupos y tarifas son comercialmente viables? Validar clientes y costos.
- **OQ-008 [pilot]** ¿Qué plazo de conservación y exportación requieren contratos y ley mexicana? ¿RPO/RTO y SLO?
- **OQ-009 [future critical]** Custodia e.firma: agente local vs KMS/HSM, alcance/consentimiento/autorización; no resuelta.
- **OQ-010 [MVP]** ¿Se incluyen notificaciones externas en MVP o sólo internas? Canales, consentimiento y frecuencia.
- **OQ-011 [MVP]** ¿Cómo se define cobro recurrente, impuestos del honorario y moneda/redondeo?
- **OQ-012 [MVP]** ¿Cómo prevenir confusión entre honorarios del despacho e impuestos del contribuyente en dashboards?
- **OQ-013 [MVP]** ¿Soportaremos relación de un contribuyente con múltiples responsables, sucursales y contactos?
- **OQ-014 [MVP security] — Open, autorización técnica parcial 2026-10-30:** BFF same-origin, OIDC Code+PKCE, sesiones JDBC privadas, Keycloak local/test, identidad global/membresías, cuatro roles, bootstrap interno e invitaciones exactas autorizados por el propietario en PR #83. Pendientes: proveedor de producción, semántica/factores MFA, recuperación/revocación federada, costos/operación e invitaciones por correo. [Guía efectiva](../security/AUTHENTICATION.md). No Resolved.
- **OQ-015 [MVP]** ¿Qué permisos se aplican a auxiliares sobre datos fiscales/financieros y archivos?
  #31 tiene aprobación parcial expresa para TENANT_SETTINGS_MANAGE de owner+MFA reciente sobre perfil propio, sin facultades fiscales ni de equipo: [registro del propietario](https://github.com/Dagt/Fiscuvia/pull/89#issuecomment-6649647209). Flyway V7 instala únicamente esa política; la matriz completa OQ-015 continúa pendiente. [Política aprobada y límites](../architecture/TENANT-PROVISIONING.md).
  En #28 permanece Open: se propone capacidad explícita por rol + asignación por operación/recurso, sin acceso ilimitado implícito para owner/supervisor. Alternativa tenant-wide requeriría capacidad distinta, aprobación y pruebas propias. Sin decisión del propietario, capacidades operativas de auxiliares y demás combinaciones propuestas permanecen DENY. [Mecanismo implementado y frontera de aprobación](../security/RESOURCE-AUTHORIZATION.md).
- **OQ-016 [MVP]** ¿Cómo proceden baja, portabilidad y suspensión de tenant sin destrucción prematura?
  #31 implementa suspensión/reactivación administrativa interna, versionada y auditada, sin eliminar historial ni atribuciones y sin reglas de cobro. No resuelve baja definitiva, retención, portabilidad o suspensión comercial: permanecen pendientes.
- **OQ-017 [future]** Qué versiones CFDI/complementos y fuente de validación deben soportarse.
- **OQ-018 [future]** Servicio SAT vigente, límites temporales, solicitud, firma y devolución; investigación técnica.
- **OQ-019 [future]** Formatos concretos de estados bancarios/criterios match y revisión humana.
- **OQ-020 [brand]** Verificar marca Fiscuvia y disponibilidad de dominio ante IMPI/registro.
- **OQ-021 [visual] — Resolved para implementación del design system, 2026-11-03:** Dagt aprobó explícitamente la opción B: especificación escrita de PR #87 como referencia para #52 ([autorización del propietario](https://github.com/Dagt/Fiscuvia/issues/52#issuecomment-6598264416)). Incluye VISUAL-SPEC, COMPONENTS-52, SCREENS-BASELINE y CURRENT-UI-AUDIT; DESIGN-SYSTEM/VISUAL-ACCEPTANCE confirmados en la solicitud. El collage original continúa ausente; no se afirma comparación contra él. [Implementación y límites #52](../ux/DESIGN-SYSTEM-52.md). No aprueba dominios fiscales #74, capacidades OQ-015 ni proveedor/MFA productivos OQ-014. #31 y #56 conservan alcances independientes.
- **OQ-022 [tech]** Matriz real de compatibilidad Java25/SpringBoot/Modulith/bibliotecas, y selección de repositorio de identidades.
- **OQ-023 [tax law]** Jurisdicción/zona de cómputo de vencimientos, prórrogas y reglas de calendario que deberán validarse antes de automatizar.
- **OQ-024 [MVP]** Uso mínimo de almacén: archivo completo o sólo metadata de e.firma/CSD para alarmas.
- **OQ-025 [MVP]** ¿Qué versión exacta de OneFacture/OneFactor se usa, qué exportaciones y fuentes tiene?
- **OQ-026 [MVP]** ¿Se necesita alguna integración con CONTPAQi Optimiza o sólo coexistir con CONTPAQi Contabilidad?

## Política de decisión

**#33, autorización parcial 2026-11-11:** el propietario aprobó invitaciones por correo exclusivamente en desarrollo/test con Keycloak, nombre/correo privados mínimos, sólo para identidades verificadas fijando issuer/subject exactos, tokens aleatorios de un uso persistidos sólo como hash y mostrados una sola vez, entrega manual sin envío externo. V10 implementa ese corte con MEMBERSHIP_INVITE existente. La aprobación de correo no autoriza proveedor/semántica productiva ni permisos de administración. La decisión posterior A sí autoriza el directorio del despacho propio, los cambios operativos de rol y la reactivación bajo owner+MFA, implementados en V11; no se autoriza un listado global de despachos. OQ-014/015 siguen Open en sus alcances restantes. [Alcance real](../security/LOCAL-EMAIL-INVITATIONS-33.md), [decisiones limitadas y pendientes](../security/TEAM-POLICY-PROPOSAL-33.md).

**OQ-014 — antecedente histórico de propuesta, anterior a la aprobación parcial (2026-10-30):** [comparación y decisiones concretas para aprobación](../decisions/issue-27-oidc-session-proposal.md). Se propone BFF OIDC estándar con emisor real sólo local de pruebas, sesiones JDBC y bootstrap de membresías limitado al actor. Requieren revisión el almacenamiento/tiempos de sesión, frontera SQL, MFA privilegiado, destinatario de invitación y primer owner. No se ha elegido proveedor de producción ni se considera esta pregunta Resolved.
No bloquear desarrollo de scaffold por preguntas comerciales no críticas, pero no implementar reglas fiscales/estados irreversibles si OQ de dominio es blocking. Marcar cambio de estatus **Resolved** sólo tras validación explícita con fecha, dueño y referencias.

## Evidencia técnica parcial de OQ-022 — issue #20, 2026-10-29

El propietario autorizó explícitamente en la conversación de #20 usar Spring Boot 4.1.2, Spring Modulith 2.1.2 y JUnit 6/Jupiter, sustituyendo la petición inicial de JUnit 5. Spring Boot 3.5.16 pasó 26 pruebas exploratorias en Java 25.0.3, pero es la [última versión con soporte OSS de la línea 3.5](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/). La base final y sus pruebas se registran en [backend/README.md](../../backend/README.md).

Esta autorización técnica se limita al scaffold #20; no convierte los ADR Proposed en Accepted. OQ-022 sigue Open para validar las futuras bibliotecas y el repositorio de identidades; OQ-014 y las preguntas fiscales permanecen abiertas.

## Implementación técnica propuesta #32

OQ-007 y ADR-015 conservan sus pendientes comerciales. V8 prepara límites técnicos/reservas, sin precios ni catálogos comerciales. La política limitada de consulta y conteo fue aprobada por Dagt en https://github.com/Dagt/Fiscuvia/pull/90#issuecomment-6798951955 y V9 activa consulta owner+MFA y protección de membresías; el alcance está descrito en [entitlements](../architecture/TENANT-ENTITLEMENTS.md); no resuelve OQ-014, OQ-015 ni #74.

### Aprobación parcial A #33 (2026-11-11)

El propietario aprobó expresamente la [política limitada de equipo](../security/TEAM-ADMINISTRATION-33.md) para owner/MFA. OQ-015 no se resuelve globalmente: no aprueba matriz fiscal, cartera ni permisos de auxiliares. OQ-014 mantiene proveedor/semántica/entrega productivos pendientes; ADR-015 sigue parcialmente pendiente.
