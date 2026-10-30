# Futuras APIs

El scaffold no realiza solicitudes HTTP ni define endpoints/DTO de negocio. `app/AppProviders.tsx` crea un QueryClient por instancia de aplicación. No se carga un tenant, una identidad ni una membresía desde valores locales.

Cuando una issue incorpore un contrato backend aprobado, su área tendrá sus DTO y consultas tipadas. Añadir aquí el transporte compartido sólo al existir ese primer consumidor: URL configurable pública, validación de respuestas en la frontera, señal de cancelación, errores seguros y autenticación autorizada. TypeScript por sí solo no valida JSON recibido. Nunca usar un cast genérico de respuesta como garantía de contrato.

No configurar tokens, secretos ni claves SAT en variables VITE_: todo valor VITE_ se publica al navegador. No instalar RHF/Zod hasta que exista un formulario o contrato que los necesite. Los providers no equivalen a una integración API ni a autorización real.
