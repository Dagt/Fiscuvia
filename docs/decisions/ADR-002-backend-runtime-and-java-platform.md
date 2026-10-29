# ADR-002 — Backend runtime and Java platform

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** NFR-040  
**Tracking:** E01 issue #20

## Contexto y problema
XML/SOAP, dominio fiscal y transacciones exigen plataforma mantenible, usando experiencia previa.

## Requisitos / restricciones
NFR-040. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Node.js TS (válido); C# (válido); fijar versión sin validar (rechazado).

## Decisión propuesta
Java 25 LTS como candidato con Spring Boot compatible; Java 21 LTS como fallback documentado tras smoke de compatibilidad.

## Razonamiento y ventajas
Reutiliza conocimiento y herramientas Java, tipado fuerte, bibliotecas XML/Security.

## Costos / consecuencias negativas
Mayor memoria de JVM, verificación de versiones/Maven plugins obligatoria.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E01 issue #20. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
