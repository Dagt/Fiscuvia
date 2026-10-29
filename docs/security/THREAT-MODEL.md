# Modelo inicial de amenazas
**Activos:** datos fiscales, contribuyentes, honorarios, documentos, sesiones, llaves futuras, backups. **Actores:** usuario tenant malicioso, empleado comprometido, soporte interno, bot, dependencia comprometida, XML/PDF/ZIP malicioso.
| Amenaza | Mitigación | Evidencia |
|---|---|---|
| IDOR entre empresas | autorización+RLS | tests API/DB/jobs con A y B |
| suplantación/cookies | OIDC, MFA, CSRF, sesión segura | pruebas auth |
| documentos accesibles por URL | signed URL después de autorización | tests de descarga |
| abuso archivo XML/ZIP | parser seguro, límites y streaming | fixtures hostiles |
| XSS/SSRF | encoding, CSP, filtrado destinos | pruebas adversariales |
| secretos expuestos | gestor de secretos, no llaves en MVP | secret scan y logging |
| exportaciones masivas | permisos, rate limits, auditoría | pruebas reporte |
| pérdida/restauración | backup cifrado, restore aislado | simulacro |
| uso SAT no autorizado | ADR de llaves + consentimiento | aprobación previa |
Actualización por cada nuevo módulo y revisión antes del piloto. No reemplaza auditoría independiente.
