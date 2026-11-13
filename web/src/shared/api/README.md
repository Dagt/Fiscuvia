# Cliente API público — #24

**Extensión efectiva C02 #35:** `taxpayer-client.ts` consume contratos reales de contribuyentes, paginación hasNext y señales de presentación cerradas. Utiliza session-client/BFF mismo origen, CSRF para POST/PATCH, Idempotency-Key, expectedVersion, AbortSignal y validación runtime de DTOs generados. No persiste ni registra RFC, nombres o búsquedas. [Contrato](../../../../docs/api/taxpayer-directory-35.md) y [flujos](../../../../docs/ux/TAXPAYER-DIRECTORY-35.md). Las notas #24 siguientes describen únicamente el cliente público original.

`createApiClient().getPlatformInfo(signal?)` consume únicamente `/api/v1/public/info`, con tipos generados del contrato implementado y validación runtime. La UI no lo invoca al montar; QueryClient continúa por instancia. No hay DTO de negocio, identidad/tenant ficticios ni nuevas pantallas.

`schema.d.ts` y `contract-metadata.ts` se generan con `npm run generate:api --prefix tools/ci` desde la raíz. `npm run openapi --prefix tools/ci` comprueba generación exacta y ambos documentos OpenAPI. No editar estos archivos a mano. TypeScript no valida JSON: el decoder verifica campos/tipos/constantes exactos antes de retornar el DTO.

Errores `ApiClientError`: code/status/traceId seguros, mensajes locales; no almacena detail/fieldErrors externos, cuerpos, tokens, cookies ni localStorage. Cancelación preservada, sin reintentos ni redirects ni logs de payloads. Nuevas áreas tendrán sus propias operaciones y decoders contra contratos revisados.

URL pública `VITE_API_BASE_URL` opcional, default `/api/v1`; `.env.example` sin secretos. Vite dev proxy `/api` a `http://127.0.0.1:8080`. Preview/producción necesitan mismo origen/gateway o CORS futuro expresamente autorizado; backend no habilita CORS global ni perfiles remotos. Se rechazan URLs con credenciales/query/fragment o protocolo no HTTP(S).

No configurar tokens, secretos ni claves SAT en variables VITE_: todo valor VITE_ se publica al navegador. No instalar RHF/Zod hasta que exista un formulario o contrato que los necesite. Los providers no equivalen a una integración API ni a autorización real.

#27 añade session-client.ts para llamadas same-origin con cookie HttpOnly, CSRF y el mismo decoder seguro de Problem Details. Nunca acepta una URL externa con credenciales ni almacena tokens. Los DTOs de identidad/membresía proceden del OpenAPI implementado; no usar el cliente público credentials:omit para sesiones. Login sigue redirects de navegador por POST; fetch no sigue redirects OAuth.
