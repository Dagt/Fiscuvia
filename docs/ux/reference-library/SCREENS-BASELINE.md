# Seis pantallas de referencia y estados — candidata v1

**No son seis pantallas implementadas.** La imagen conceptual falta; esta especificación escrita es alternativa para aprobación humana ([registro](README.md)). Todos los layouts precisos son PROPUESTA POR APROBAR, con [componentes y medidas comunes](VISUAL-SPEC.md). La marca y ES-MX conservan la dirección autorizada. Contribuyente es entidad del despacho; evitar “cliente” ambiguo como tenant.

## S01 — Acceso /login

IMPLEMENTADO OIDC local/test, no formulario de email/contraseña de Fiscuvia. Desktop: panel navy42% con marca/eslogan, columna derecha con título, explicación, CTA único “Continuar con acceso seguro”, nota de privacidad y pie. Formato actual máx460px; móvil390: marca arriba, contenido342px con padding24. Recuperación/MFA y credenciales pertenecen al proveedor realmente configurado; no agregar botones SSO, registro o recuperación inoperantes.

Estados: inicial/carga del servicio; disponible; configuración ausente; conexión fallida con reintento; rechazo OIDC; sesión expirada. El error no expone identidad, proveedor interno ni parámetros. Autenticado va a selección de despacho real, no dashboard. Teclado alcanza CTA y regreso; loading anunciado. No prometer proveedor productivo. FR-002/003, #27 implementado local/test; #52 sólo consistencia del kit. La antigua frase “correo/recuperación” en SCREENS describe propuesta histórica, no instrucción de pedir contraseña en el BFF.

## S02 — Dashboard /app (CONCEPTO FUTURO, #53)

Hoy /app es selección/estado de sesión real. Futuro: shell240/header66; a1440 ancho útil1136; PageHeader “Resumen del despacho”, período y última actualización. Máximo cuatro StatCard pertinentes autorizadas, luego área principal8 columnas con próximos pendientes y secundaria4 para actividad; evitar repetir cada dato en una tarjeta o añadir donut decorativo. CTA principal apunta a una tarea existente según permisos, no “crear” universal. Filtros debajo de título antes de métricas, compartidos para todas las secciones afectadas.

Móvil: título→período→resumen en bloques legibles→lista priorizada→actividad. No sustituir la lista por una matriz comprimida; métricas una/dos columnas sólo si el texto cabe. Sin datos: explicar qué módulo produce información, no inventar cifras cero. Error parcial conserva secciones válidas y fecha; error total con reintento; 403 no muestra métricas. Los montos de honorarios se rotulan aparte de impuestos. Nombres largos envuelven, CTA no se desplaza fuera del viewport. FR-065/070/071, BR-060, #53 depende de módulos reales; nada de esto se implementa en #19/#52.

## S03 — Directorio /app/taxpayers (CONCEPTO FUTURO, #34–35)

PageHeader “Contribuyentes”, CTA “Nuevo contribuyente” sólo si backend autoriza. Ancho útil común1136/976; FilterBar debajo con búsqueda etiquetada, estado y responsable/régimen cuando existan contratos revisados; limpiar filtros y contar sólo resultados autorizados. DataTable blanca con columnas nombre flexible, RFC, régimen, responsable, estado y acción de detalle; no celda entera con acciones ambiguas ni alerta sin fuente. Paginación debajo con total autorizado, no total de todo el despacho si la cartera es parcial.

Móvil390: filtros en disclosure etiquetado, búsqueda visible, lista con nombre y campos clave etiquetados; enlace explícito “Ver expediente”; acciones administrativas en menú accesible. Longitud100 de nombre admite dos líneas y acceso completo por detalle, sin tooltip sólo hover. Estados: primera cartera vacía con CTA autorizada; filtro sin resultados con limpiar; loading preserva encabezado; error reintentar; 401/403/404 uniformes según backend. Sin paginar/filtros en frontend para corregir una respuesta demasiado amplia. FR-012–017; OQ-013/015 y #74 no resueltos por diseño.

## S04 — Ficha /app/taxpayers/:id (CONCEPTO FUTURO, #36)

Breadcrumb a directorio, encabezado nombre multilínea, RFC/estado/responsable con etiquetas; primaria “Editar datos” sólo autorizada. Tabs Resumen/Obligaciones/Documentos/Honorarios/Historial únicamente cuando su módulo/permiso exista; no tabs accionables vacíos que simulen operación. Desktop: área principal mínimo dos tercios y DetailsPanel lateral320–360 si el espacio permite; resúmenes útiles, no veinte tarjetas. Metadatos de certificados jamás llaves/contraseñas. Alertas con tipo/origen/fecha, nunca inferir estatus SAT.

Móvil: encabezado→acciones→secciones, tabs desplazables con teclado o selector accesible, detalles debajo; no drawer obligatorio para toda tarea. Estados por sección loading/empty/error/denied, recurso ajeno/no asignado no enumerable; conflicto409 preserva cambios y ofrece recargar. Nombres/documentos largos no empujan acciones; encabezado de dato completo accesible. FR-018/031/050, #36/#47/#51 y gate#74. Historial visual no habilita AUDIT_VIEW aún no aprobado.

## S05 — Centro CFDI /app/cfdi (CONCEPTO FUTURO posterior, #62–64)

No aparece como módulo disponible hoy. Futuro: PageHeader “CFDI”, procedencia/fecha de consulta, tabs Emitidos/Recibidos, filtros de período/tipo/estado sustentado y contribuyente autorizado. CTA Importar sólo cuando exista módulo seguro; descarga SAT no implícita. Tabla UUID abreviado con copia accesible del valor autorizado, emisor/receptor permitido, fecha, tipo/importe/moneda, estado con fuente y acción. DetailsPanel lateral400 candidato; móvil ruta detalle a ancho completo y lista de comprobantes, no once columnas a12px.

Estados: sin importaciones, sin resultados, procesamiento pendiente real, error de archivo/contrato, descarga no autorizada y fuente de estado no disponible. “Sin verificar” no se convierte en “Vigente”. No convertir montos sintéticos en métricas de clientes ni XML ficticio en factura operativa. FR-080–085, BR-070–074; producción CFDI/SAT/firma y gate#74 siguen sujetos a sus issues. Aquí sólo se describe composición futura.

## S06 — Calendario /app/obligations (CONCEPTO FUTURO, #38–42)

PageHeader “Obligaciones”, selector período + navegación anterior/hoy/siguiente con nombres claros; filtros contribuyente/responsable/estado antes de vistas Calendario/Lista/Matriz. Desktop1136/976: siete columnas con eventos resumidos y “ver más” cuando exceda altura; no limitar información a puntos de color. Cada evento contiene título abreviado legible, contribuyente autorizado, fecha y estado pertinente; detalle lateral de400 sólo si acción existe. Lista accesible siempre disponible; matriz densa requiere contrato y datos reales del módulo.

Móvil390: agenda/lista como vista inicial, período arriba y filtros plegables; calendario mensual opcional con día seleccionado y lista debajo, sin texto fiscal comprimido dentro de celdas. Nombres completos en detalle. Distinguir trabajo, presentación y pago en grupos etiquetados; no mezclar una declaración presentada con impuesto pagado. Sin plantillas: first-use guiado al módulo permitido, no calendario SAT heurístico. Sin eventos filtrados: limpiar; error/reintento; permiso revocado: ocultar datos; conflicto: conservar intención y recargar. FR-020–030, BR-024–029, OQ-001–006/023 y #74 pendientes.

## Estados y criterios transversales

| Estado | Evidencia/comportamiento requerido en cada módulo futuro |
|---|---|
| Loading | espacio estable, status/aria-busy, sin duplicar anuncios; skeleton decorativo y reduced-motion |
| Primera vez / vacío filtrado | explicación diferente, acción real autorizada; no falsos ceros ni CTA de módulo ausente |
| Error recuperable / offline | mensaje seguro, retry real, conserva filtros/intención; no stack, SQL, UUID ajeno ni token |
| 401 / 403 / 404 | sesión expirada lleva a acceso; 403 no insinúa dato ajeno; 404 para ocultamiento según contrato backend |
| Validación /409/422/429 | errores asociados, no borrar captura; conflicto explícito y reintento seguro; mostrar espera sólo si servidor la indica |
| Nombres largos / cifras grandes | fixtures DEMO sólo en catálogo/módulo de pruebas; wrapping y acceso al valor completo sin sólo hover |
| Responsive / zoom | 1440,1280,768,390; reflow320 CSS px,200% texto/zoom, foco visible y no cubierto; excepción de tabla bidimensional explícita |
| Permisos / cambio de despacho | backend decide, UI nunca recibe ALL por jerarquía; limpiar filas/filtros sensibles/contexto viejo antes de mostrar otro despacho |

Las rutas y capacidades son contratos visuales futuros, no permisos aprobados. Catálogo Proposed/ADR no se convierten en Accepted mediante este documento. El componente común #52 debe soportar estados sin construir estas pantallas de negocio.
