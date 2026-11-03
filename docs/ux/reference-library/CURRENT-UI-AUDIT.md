# Auditoría comparativa — frontend efectivo

Base UI: main `3621a7d1e319f6ec8bbedad5db015d4a98a8591c`. [Capturas nuevas y manifest](../evidence/issue-19/README.md), [candidata escrita](VISUAL-SPEC.md). La comparación es **contra documentos escritos**, no contra píxeles del collage ausente. No hay calificación de fidelidad exacta a esa imagen ni aprobación visual automática.

## Pantallas realmente existentes

| Ruta / evidencia | Qué se ve hoy | Qué propone la referencia | Diferencia y destino |
|---|---|---|---|
| /login, login-1440/390 | panel navy42%, marca/eslogan, bloque de acceso máximo460px y CTA teal; móvil marca arriba; OIDC real disponible en harness | conservar marca, geometría y un CTA; consolidar tokens y semántica de encabezados | Reutilizar AuthFrame/Brand/Button. No existe card blanca bordeada ni formulario de correo local; la antigua descripción conceptual no autoriza añadirlo. #52 armoniza UI, producción IdP pendiente |
| /app, seleccion-1440/390 | dos botones “Despacho 1/2” que proceden de membresías reales; rol ES-MX, cierre de sesión | contexto autorizado visible y consistente con shell; nombres/logos sólo del backend futuro | Hoy no usa AppShell ni muestra nombres comerciales. #52 compone; #31 provee datos de marca. No reemplazar ordinal por nombre inventado |
| /app, autorizado-1440/390 | “Tu acceso está listo”, rol, mensaje de áreas fiscales futuras y cambiar despacho | continuidad del contexto/estado sin fingir dashboard | No hay métricas, expedientes ni dashboard. #53 los implementará cuando existan módulos; #52 no los simula |
| /app, sin-membresias-1440 | identidad verificada y explicación de invitación, logout | estado vacío específico distinto de 403/error de login | Reutilizable; no añadir alta de owner pública ni selector vacío ficticio |
| /login error-autenticacion-1440/390 | rechazo real de state OIDC alterado, título seguro y CTA de nuevo inicio | error accionable y seguro con la misma identidad | Mantener; no se muestran detalles del proveedor ni tokens; no rediseñar Keycloak |
| /login sesion-expirada-390 | sesión invalidada por logout y posterior acceso /app; aviso de volver a iniciar | estado de expiración distinguible y foco/lectura coherentes | Reutilizar mensaje; pruebas de expiración absoluta/inactividad siguen en suite de seguridad, no se sustituyen por esta captura |
| /desarrollo shell-1440/390 | sidebar navy240, topbar66 desktop/64 móvil, bienvenida/hero y áreas futuras deshabilitadas; contexto “Sin despacho conectado” | shell común reutilizable con datos autorizados sólo cuando correspondan | No confundir hero de desarrollo con dashboard productivo ni etiqueta con selector real; #52 extrae contratos sin habilitar negocios |
| /desarrollo menu-390 | Radix Dialog navy, overlay, cierre, enlaces reales de demo y próximos deshabilitados | mantener foco confinado/Escape/retorno; destino de navegación enfocado | Patrón reutilizable de navegación. No equivale a SideDrawer de recurso ni ConfirmationDialog |
| /desarrollo/estados | galería rotulada de vacío/carga/error/permisos, retry local notificado | estados reutilizables con acción real en módulos posteriores | Sólo demo de presentación. No acredita denegación real de un expediente ni conexión de negocio |
| cualquier otra ruta | NotFound con vuelta al inicio de desarrollo | salida contextual en shell de cada área | Existe componente; #52 debe separar destino de retorno de desarrollo del producto autenticado |

## Inventario real y medidas

Fuente: CSS/TSX inspeccionados y geometría de Chromium en capture-manifest.json. Inter Variable importada/autoalojada; cuerpo14px/1.6, h1 shell28 desktop/24 móvil, títulos auth28/24, marca principal46máx/29 móvil. AppShell tiene max1440, padding32 desktop; móvil24 vertical/16 horizontal; tableta28 vertical/24 horizontal. Menú pasa a drawer en1023px; auth cambia a columna en640px. Button44px y CTAauth48; radioButton8/panel12. Esos valores son **IMPLEMENTADOS**, no prueba de aprobación del layout final.

| Área | Reutilizable ahora | Cambio preparado para #52 | Espera a otro issue |
|---|---|---|---|
| Colores | navy/teal/lienzo/tinta y muted oscurecido | nombres semánticos, eliminar dispersión de hex; borde.control y estados propuestos con contraste | marca persistente de tenant #31; no sobreescribir seguridad con colores comerciales |
| Tipografía | Inter local y cifras tabulares | escala coherente; varios auxiliares auth11px y estilos12/13 no están tokenizados; objetivo propuesto mínimo12 | decisiones fiscales/formatos de importes por módulo |
| Encabezados | PageHeader y AuthHeading | eyebrow configurable; auth usa h1 para eslogan y h2 para título, revisar semántica sin cambiar visual de marca; un título principal por vista | contenido definitivo de pantallas de negocio |
| Navegación | NavLink aria-current, botones futuros deshabilitados, Radix accesible | separar navegación de desarrollo y sesión, slot autorizado, evitar main anidado al componer | búsqueda/campana/usuario/acciones sólo cuando existan funciones |
| Componentes | Button 3 variantes, cinco estados, notificación cerrable | añadir catálogo controlado, inputs/tabla/filtros/pills/dialog/detalles según contratos | no habilitar matrices, fiscal periods o uploader operativo |
| Responsive | navegación funciona y no requiere comprimir texto | unificar breakpoints y tokens de separación ad-hoc26/22/18px; probar nombres largos que hoy no existen en selector ordinal | datos reales de marca/nombres #31 y módulos de contribuyentes |
| Scroll entre vistas | scroll vertical legítimo, pero una transición de sesión puede conservar desplazamiento anterior | definir restauración/foco al cambiar de vista; las capturas se toman explícitamente al inicio del documento para comparar, sin corregir producto | comportamiento específico por módulo cuando exista navegación de detalle |
| Accesibilidad | foco3px, skip-link shell, menú con trap/restore, reduced-motion | incorporar contratos de campos/tabla/dialog y verificar zoom/reflow por componente | auditoría completa de workflows futuros y #56 visual diffs |

El borde tenue actual contrasta1.27:1 sobre blanco: es válido como separación decorativa, **no** se debe copiar como única frontera de un input nuevo. selected3.49:1 no admite texto blanco normal; primary4.88:1 sí. `--muted` actual #536B80 difiere del #667A8F documental, que sólo da4.43:1 sobre blanco. Estos son hallazgos verificables y propuestas de uso, no cambios CSS de esta entrega.

## Evidencia y límites de evaluación

Capturas Chromium con proveedor real sólo local y perfiles sintéticos, sin nombres personales ni fiscales, RFC/UUID visibles, credenciales o screenshots del proveedor. La geometría, dimensiones y hashes se verifican por script; axe A/AA relevante y checks de teclado/overflow se registran. No se modifica DOM para aparentar nombres largos/tablas/acciones que no existen. La aceptación manual sigue pendiente: legibilidad, densidad y jerarquía se revisan junto a estas imágenes; no se autoconcede ≥4 en la rúbrica ni se declara conformidad WCAG completa por axe.

Los casos100+ filas, controles de formularios, nombres100 caracteres, importes grandes y tablas móviles son aceptación **PENDIENTE de #52 o del módulo**, no checks aprobados de este PR. Las cinco pantallas conceptuales de negocio no tienen screenshots operativos ni fixtures fiscales agregados. #74/OQ-015/proveedor productivo continúan pendientes.
