# ADR-006 — Private object storage and secure upload

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** FR-050..056  
**Tracking:** E07 #46–48

## Contexto y problema
CFDI, constancias, acuses y PDF de varios tenants sin exposición pública.

## Requisitos / restricciones
FR-050..056. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
BLOB binario masivo en DB; bucket público; upload a través JSON.

## Decisión propuesta
Almacenamiento S3/R2 privado; metadatos en PostgreSQL; link temporal tras autorización; finalización/escaneo.

## Razonamiento y ventajas
Costos controlables, escalabilidad, privacidad y auditoría.

## Costos / consecuencias negativas
Hash/MIME/antivirus, cuotas y orphan cleanup; integración completa en E07.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E07 #46–48. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
