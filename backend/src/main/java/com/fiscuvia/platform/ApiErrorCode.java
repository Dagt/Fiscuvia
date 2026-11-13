package com.fiscuvia.platform;

/** Stable transport codes. No exception message or caller input becomes a public detail. */
public enum ApiErrorCode {
    BAD_REQUEST(400, "Solicitud inválida", "Revisa el formato de la solicitud."),
    INVALID_PAGINATION(400, "Paginación inválida", "Revisa los límites y el orden solicitado."),
    AUTHENTICATION_REQUIRED(401, "Autenticación requerida", "Se requiere una autenticación válida."),
    ACCESS_DENIED(403, "Acceso no permitido", "No tienes permiso para realizar esta operación."),
    NOT_FOUND(404, "Recurso no encontrado", "El recurso solicitado no está disponible."),
    METHOD_NOT_ALLOWED(405, "Método no permitido", "El método HTTP no está disponible para este recurso."),
    NOT_ACCEPTABLE(406, "Formato no disponible", "El formato de respuesta solicitado no está disponible."),
    QUOTA_UNAVAILABLE(409, "Cupo no disponible", "No hay cupo disponible para esta operación. Solicita al equipo de Fiscuvia revisar la configuración y los límites del despacho."),
    CONFLICT(409, "Conflicto", "La operación entra en conflicto con el estado actual."),
    STALE_VERSION(409, "Versión desactualizada", "Actualiza la información antes de volver a intentarlo."),
    IDEMPOTENCY_KEY_REUSED(409, "Clave de idempotencia reutilizada", "La clave ya se utilizó para otra solicitud."),
    PAYLOAD_TOO_LARGE(413, "Solicitud demasiado grande", "La solicitud supera el tamaño permitido."),
    UNSUPPORTED_MEDIA_TYPE(415, "Formato de solicitud no admitido", "Utiliza el tipo de contenido indicado en el contrato."),
    VALIDATION_FAILED(422, "Datos inválidos", "Revisa los campos indicados antes de continuar."),
    RATE_LIMITED(429, "Límite de solicitudes", "Espera antes de volver a intentarlo."),
    INTERNAL_ERROR(500, "Error interno", "No fue posible completar la solicitud. Conserva el identificador de seguimiento.");

    private final int status;
    private final String title;
    private final String detail;

    ApiErrorCode(int status, String title, String detail) {
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public int status() { return status; }
    public String title() { return title; }
    public String detail() { return detail; }

    public static ApiErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> AUTHENTICATION_REQUIRED;
            case 403 -> ACCESS_DENIED;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 422 -> VALIDATION_FAILED;
            case 429 -> RATE_LIMITED;
            default -> INTERNAL_ERROR;
        };
    }
}
