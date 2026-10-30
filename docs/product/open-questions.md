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
- **OQ-014 [MVP security]** Proveedor OIDC, tipo de sesión, MFA y recuperación: decisión concreta.
- **OQ-015 [MVP]** ¿Qué permisos se aplican a auxiliares sobre datos fiscales/financieros y archivos?
- **OQ-016 [MVP]** ¿Cómo proceden baja, portabilidad y suspensión de tenant sin destrucción prematura?
- **OQ-017 [future]** Qué versiones CFDI/complementos y fuente de validación deben soportarse.
- **OQ-018 [future]** Servicio SAT vigente, límites temporales, solicitud, firma y devolución; investigación técnica.
- **OQ-019 [future]** Formatos concretos de estados bancarios/criterios match y revisión humana.
- **OQ-020 [brand]** Verificar marca Fiscuvia y disponibilidad de dominio ante IMPI/registro.
- **OQ-021 [visual]** Incorporar archivo de mockup aprobado al repo con autorización; definir baseline de capturas reales.
- **OQ-022 [tech]** Matriz real de compatibilidad Java25/SpringBoot/Modulith/bibliotecas, y selección de repositorio de identidades.
- **OQ-023 [tax law]** Jurisdicción/zona de cómputo de vencimientos, prórrogas y reglas de calendario que deberán validarse antes de automatizar.
- **OQ-024 [MVP]** Uso mínimo de almacén: archivo completo o sólo metadata de e.firma/CSD para alarmas.
- **OQ-025 [MVP]** ¿Qué versión exacta de OneFacture/OneFactor se usa, qué exportaciones y fuentes tiene?
- **OQ-026 [MVP]** ¿Se necesita alguna integración con CONTPAQi Optimiza o sólo coexistir con CONTPAQi Contabilidad?

## Política de decisión

**OQ-014 — propuesta pendiente de #27 (2026-10-30):** [comparación y decisiones concretas para aprobación](../decisions/issue-27-oidc-session-proposal.md). Se propone BFF OIDC estándar con emisor real sólo local de pruebas, sesiones JDBC y bootstrap de membresías limitado al actor. Requieren revisión el almacenamiento/tiempos de sesión, frontera SQL, MFA privilegiado, destinatario de invitación y primer owner. No se ha elegido proveedor de producción ni se considera esta pregunta Resolved.
No bloquear desarrollo de scaffold por preguntas comerciales no críticas, pero no implementar reglas fiscales/estados irreversibles si OQ de dominio es blocking. Marcar cambio de estatus **Resolved** sólo tras validación explícita con fecha, dueño y referencias.

## Evidencia técnica parcial de OQ-022 — issue #20, 2026-10-29

El propietario autorizó explícitamente en la conversación de #20 usar Spring Boot 4.1.2, Spring Modulith 2.1.2 y JUnit 6/Jupiter, sustituyendo la petición inicial de JUnit 5. Spring Boot 3.5.16 pasó 26 pruebas exploratorias en Java 25.0.3, pero es la [última versión con soporte OSS de la línea 3.5](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/). La base final y sus pruebas se registran en [backend/README.md](../../backend/README.md).

Esta autorización técnica se limita al scaffold #20; no convierte los ADR Proposed en Accepted. OQ-022 sigue Open para validar las futuras bibliotecas y el repositorio de identidades; OQ-014 y las preguntas fiscales permanecen abiertas.
