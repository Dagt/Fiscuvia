# Estrategia CFDI y SAT (no incluida en MVP inicial)
## Fase A — importar XML sin SAT
Cargar XML/ZIP autorizados, procesar por streaming, validar tamaños/estructuras/versiones y desactivar XXE; extraer UUID, RFC emisor/receptor, fechas, importes, moneda, tipo, complementos cuando proceda. Guardar original en almacenamiento privado; indexar metadatos y deduplicar por tenant/cliente/UUID/contenido. Mostrar origen y fecha de verificación; un CFDI descargado no demuestra por sí solo vigencia actual. Exportar filtrado a Excel.
## Fase B — servicio oficial de descarga
Investigar versión vigente de servicios web del SAT, formatos, autenticación, límites/histórico, plazos, cuotas y requisitos con fuentes primarias **durante la implementación**. Flujo: solicitud autorizada -> job persistente -> autenticación/firma con mecanismo aprobado -> sondeo acotado -> paquete -> descompresión segura -> deduplicación -> resultado/alerta. Reintentos acotados, idempotencia, errores, costos y cuotas por tenant. Nunca prometer resultado inmediato.
## Bóveda/llaves
Requiere ADR-0003 aprobada, modelo de amenaza, revisión legal, consentimiento y políticas de custodia. No .key en localStorage/DB en texto plano.
## Fase C — conciliación asistida
Importación de estados bancarios por formatos soportados, coincidencias propuestas (fecha/importe/referencia), revisión humana y trazabilidad. No cálculo automático de impuestos ni declaración al SAT.
## Pruebas
Respuestas fallidas SAT, expiración firma, ZIP truncado/bomba, XML hostil, status obsoleto, dos tenants concurrentes, duplicados, reanudación jobs, cuota.
