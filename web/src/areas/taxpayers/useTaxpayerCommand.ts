import { useEffect, useRef, useState } from 'react'
import {
  taxpayerCommand,
  type Command,
  type Receipt,
} from '../../shared/api/taxpayer-client'
import { ApiClientError } from '../../shared/api/client'

export function useTaxpayerCommand(
  tenant: string,
  onConfirmed: (r: Receipt) => void,
  onUnavailable: (status: number) => void,
  onRetain?: ((retain: boolean) => void) | undefined,
) {
  const [busy, setBusy] = useState(false),
    [uncertain, setUncertain] = useState(false),
    [error, setError] = useState<ApiClientError | null>(null)
  const stored = useRef<{ command: Command; key: string } | null>(null),
    live = useRef(true),
    inFlight = useRef(false),
    controller = useRef<AbortController | null>(null)
  useEffect(() => {
    onRetain?.(busy || uncertain)
  }, [busy, uncertain, onRetain])
  useEffect(() => () => onRetain?.(false), [onRetain])
  useEffect(() => {
    live.current = true
    return () => {
      live.current = false
      controller.current?.abort()
      stored.current = null
    }
  }, [])
  async function run(next?: Command) {
    if (inFlight.current) return false
    if (!stored.current) {
      if (!next) return false
      stored.current = {
        command: structuredClone(next),
        key: crypto.randomUUID(),
      }
    }
    const operation = stored.current
    inFlight.current = true
    setBusy(true)
    setError(null)
    controller.current = new AbortController()
    const timeout = setTimeout(() => controller.current?.abort(), 30000)
    try {
      const receipt = await taxpayerCommand(
        tenant,
        operation.command,
        operation.key,
        controller.current.signal,
      )
      if (!live.current) return false
      stored.current = null
      setUncertain(false)
      onConfirmed(receipt)
      return true
    } catch (failure) {
      if (!live.current) return false
      const e =
        failure instanceof ApiClientError
          ? failure
          : new ApiClientError('NETWORK_ERROR')
      const unknown =
        e.status === undefined ||
        e.status >= 500 ||
        (e.status >= 200 && e.status < 300)
      setError(e)
      setUncertain(unknown)
      if (!unknown) stored.current = null
      if (e.status === 401 || e.status === 403 || e.status === 404)
        onUnavailable(e.status)
      return false
    } finally {
      clearTimeout(timeout)
      inFlight.current = false
      if (live.current) setBusy(false)
    }
  }
  return { run, busy, uncertain, error }
}
export function commandMessage(error: ApiClientError | null, creating = false) {
  if (!error) return ''
  if (error.code === 'QUOTA_UNAVAILABLE')
    return 'No hay cupo disponible o el límite no está vigente. Solicita revisar el plan del despacho.'
  if (error.code === 'STALE_VERSION')
    return 'La información cambió mientras trabajabas. Actualiza los datos y revísalos antes de volver a guardar.'
  if (error.code === 'IDEMPOTENCY_KEY_REUSED')
    return 'El identificador de esta solicitud ya se utilizó para otra operación. Revisa el directorio antes de volver a intentarlo.'
  if (error.code === 'CONFLICT')
    return creating
      ? 'No se pudo registrar el contribuyente. El RFC puede estar registrado, incluso archivado. Solicita una revisión autorizada.'
      : 'La operación no es compatible con el estado actual o no hay cambios. Actualiza la información.'
  if (error.code === 'VALIDATION_FAILED' || error.status === 400)
    return 'Revisa los campos y las referencias de régimen antes de continuar.'
  if (error.status === 401 || error.status === 403 || error.status === 404)
    return 'No se pudo autorizar la operación. Tu acceso, estado o verificación MFA pueden haber cambiado.'
  if (error.status !== undefined && error.status >= 400 && error.status < 500)
    return 'No se pudo completar la operación. Revisa la información y vuelve a intentarlo si tu acceso continúa vigente.'
  return 'No pudimos confirmar el resultado. Conservamos la misma solicitud en esta vista; recupera su resultado antes de iniciar otra operación.'
}
