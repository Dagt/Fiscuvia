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
