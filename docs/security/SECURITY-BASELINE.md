# Seguridad y privacidad: criterios de liberación
## Clasificación
Público / operación interna / confidencial de despacho / fiscal sensible / credenciales criptográficas (prohibidas en MVP).
## Controles
OIDC confiable, MFA para roles privilegiados, permisos de despacho+recurso, invitaciones revocables, sesiones seguras; TLS, cifrado reposo, gestor de secretos, logs sin PII, CSP, protección CSRF/XSS/SSRF, límites de abuso; archivos privados con hash, acceso firmado autorizado, validación MIME y parser XML/ZIP endurecido. RLS y pruebas cruzadas.
## Operaciones
Auditoría append-only orientada a integridad, backups automáticos cifrados y restauración demostrada; retención/eliminación y datos de soporte controlados. Entornos aislados. Política de respuesta a incidentes, vigilancia de dependencias, escaneo de secretos.
## Legal
Avisos de privacidad, roles responsable/encargado, contratos de tratamiento, derechos de titulares, subprocesadores, retención, exportación y borrado sujetos a revisión legal mexicana antes del piloto con datos reales. No afirmar certificación ni cumplimiento garantizado.
## Gate piloto
Threat model actualizado, revisión independiente adecuada al riesgo, tests tenant/RLS, restauración, configuración OIDC/MFA, revisión legal/contratos, monitoreo y aceptación de seguridad. Sin ello usar sólo datos sintéticos.

## Base verificable #26 (para revisión)

[Tenancy física y contrato transaccional](../data-model/tenant-foundation-26.md): runtime sin ownership/roles DB/privilegios de bypass, FORCE RLS y cerca restrictiva, FKs tenant-first y contexto privado por xid real con GUCs locales. Negación por defecto y pruebas de pool/concurrencia, SQL sin filtros, forja de GUC, checksum/rollback y resistencia a relajación de políticas. Runtime no puede modificar la raíz tenants ni el registro privado. No existe endpoint de selección/diagnóstico tenant; logs/métricas/trazas no reciben el tenant.

RLS no autentica ni autoriza un UUID: #27/#28 deben resolver identidad, membresía, capacidades y recurso antes de llamar el puerto interno. Credenciales runtime comprometidas o código interno malicioso quedan fuera de esa garantía; el migrador/DBA puede alterar DDL y necesita custodia separada. PostgreSQL local/tests suprime errores SQL/sentencias/parámetros que podrían contener valores privados, con prueba canario; no ampliar logging sin revisión. El piloto sigue bloqueado por los gates restantes, no sólo por tests de DB.
