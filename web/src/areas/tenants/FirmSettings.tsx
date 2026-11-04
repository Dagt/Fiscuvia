import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, ShieldCheck } from 'lucide-react'
import { ApiClientError } from '../../shared/api/client'
import {
  firmSettings,
  firmTimeZones,
  updateFirmSettings,
  type FirmSettings as Profile,
  type UpdateFirmSettings,
} from '../../shared/api/firm-client'
import { Button } from '../../shared/ui/button'
import { Field } from '../../shared/ui/Field'
import { ConfirmationDialog } from '../../shared/ui/Panels'
import {
  ErrorState,
  LoadingState,
  PermissionState,
} from '../../shared/ui/States'
import { StatusPill } from '../../shared/ui/StatusPill'
import { useNotification } from '../../shared/presentation/notification-context'
import './firm-settings.css'

const zoneLabels: Record<Profile['timeZone'], string> = {
  'America/Mexico_City': 'Ciudad de México',
  'America/Cancun': 'Cancún',
  'America/Chihuahua': 'Chihuahua',
  'America/Ciudad_Juarez': 'Ciudad Juárez',
  'America/Hermosillo': 'Hermosillo',
  'America/Mazatlan': 'Mazatlán',
  'America/Monterrey': 'Monterrey',
  'America/Tijuana': 'Tijuana',
}

export function FirmSettings({
  userId,
  tenantId,
  onUnavailable,
}: {
  userId: string
  tenantId: string
  onUnavailable: (status: number) => void
}) {
  const query = useQuery({
    queryKey: ['firm-settings', userId, tenantId],
    queryFn: async ({ signal }) => {
      try {
        return await firmSettings(tenantId, signal)
      } catch (error) {
        if (
          error instanceof ApiClientError &&
          (error.status === 401 || error.status === 403)
        )
          onUnavailable(error.status)
        throw error
      }
    },
    retry: false,
    staleTime: 0,
  })
  if (query.isPending || query.isFetching)
    return <LoadingState title="Consultando tu despacho" />
  if (query.isError)
    return query.error instanceof ApiClientError &&
      query.error.status === 403 ? (
      <PermissionState />
    ) : (
      <ErrorState onRetry={() => void query.refetch()} />
    )
  if (query.data.status !== 'ACTIVE')
    return (
      <PermissionState
        title="Despacho suspendido"
        description="El acceso operativo está suspendido. Contacta al equipo de Fiscuvia para revisar el estado administrativo."
      />
    )
  return (
    <FirmForm
      key={`${userId}:${tenantId}:${query.data.version}`}
      profile={query.data}
      tenantId={tenantId}
      onUnavailable={onUnavailable}
    />
  )
}
function FirmForm({
  profile,
  tenantId,
  onUnavailable,
}: {
  profile: Profile
  tenantId: string
  onUnavailable: (status: number) => void
}) {
  const [name, setName] = useState(profile.commercialName ?? '')
  const [email, setEmail] = useState(profile.contactEmail ?? '')
  const [phone, setPhone] = useState(profile.phone ?? '')
  const [zone, setZone] = useState(profile.timeZone)
  const [failure, setFailure] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [pending, setPending] = useState(false)
  const form = useRef<HTMLFormElement>(null)
  const alive = useRef(true)
  const command = useRef<{ fingerprint: string; key: string } | null>(null)
  const notify = useNotification()
  const client = useQueryClient()
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  const dirty =
    name !== (profile.commercialName ?? '') ||
    email !== (profile.contactEmail ?? '') ||
    phone !== (profile.phone ?? '') ||
    zone !== profile.timeZone
  function cancel() {
    setName(profile.commercialName ?? '')
    setEmail(profile.contactEmail ?? '')
    setPhone(profile.phone ?? '')
    setZone(profile.timeZone)
    setFailure(null)
    setFieldErrors({})
    command.current = null
  }
  async function save() {
    if (!form.current?.reportValidity() || pending)
      throw new Error('Invalid form')
    const body: UpdateFirmSettings = {
      expectedVersion: profile.version,
      commercialName: name.trim(),
      contactEmail: email.trim() || null,
      phone: phone.trim() || null,
      timeZone: zone,
      locale: 'es-MX',
    }
    const fingerprint = JSON.stringify(body)
    if (command.current?.fingerprint !== fingerprint)
      command.current = { fingerprint, key: crypto.randomUUID() }
    setPending(true)
    setFailure(null)
    try {
      await updateFirmSettings(tenantId, body, command.current.key)
      if (!alive.current) return
      notify('Datos del despacho guardados')
      void client.invalidateQueries({ queryKey: ['firm-settings'] })
      void client.invalidateQueries({ queryKey: ['current-identity'] })
    } catch (error) {
      if (!alive.current) return
      if (
        error instanceof ApiClientError &&
        (error.status === 401 || error.status === 403)
      )
        onUnavailable(error.status)
      else
        setFailure(
          error instanceof ApiClientError && error.status === 409
            ? 'La información cambió o la solicitud entra en conflicto. Cancela y vuelve a consultar antes de guardar.'
            : error instanceof ApiClientError && error.status === 422
              ? 'Revisa los datos. El servidor no aceptó el formato de uno o más campos.'
              : 'No pudimos guardar los cambios. Puedes reintentar; conservamos la misma clave para evitar duplicados.',
        )
      throw error
    } finally {
      if (alive.current) setPending(false)
    }
  }
  return (
    <div className="firm-settings">
      <section className="firm-brand" aria-labelledby="firm-brand-title">
        <span className="firm-monogram" aria-hidden="true">
          <Building2 size={28} />
        </span>
        <div>
          <h2 id="firm-brand-title">Identidad de tu despacho</h2>
          <p className="firm-name">
            {profile.commercialName ?? 'Nombre comercial pendiente'}
          </p>
          <p>Sin logo</p>
        </div>
        <StatusPill tone="success">Activo</StatusPill>
      </section>
      <section className="firm-card" aria-labelledby="firm-data-title">
        <div className="firm-section-heading">
          <div>
            <h2 id="firm-data-title">Datos del despacho</h2>
            <p>
              Información compartida únicamente con integrantes autorizados.
            </p>
          </div>
          <ShieldCheck size={22} aria-hidden="true" />
        </div>
        {!profile.canManage && (
          <p className="firm-readonly" role="status">
            Sólo consulta. Para editar necesitas permiso y una verificación
            reciente con doble factor.
          </p>
        )}
        <form
          ref={form}
          onSubmit={(event) => event.preventDefault()}
          onInvalid={(event) => {
            event.preventDefault()
            const input = event.target as HTMLInputElement
            if (
              ['commercialName', 'contactEmail', 'phone'].includes(input.name)
            )
              setFieldErrors((previous) => ({
                ...previous,
                [input.name]: input.validity.valueMissing
                  ? 'Este campo es obligatorio.'
                  : 'Revisa el formato de este campo.',
              }))
          }}
          aria-label="Configuración del despacho"
        >
          <fieldset disabled={pending} className="firm-fields">
            <Field
              label="Nombre comercial"
              name="commercialName"
              error={fieldErrors.commercialName ?? ''}
              required
              maxLength={160}
              value={name}
              readOnly={!profile.canManage}
              onChange={(event) => {
                setName(event.target.value)
                setFieldErrors((previous) => ({
                  ...previous,
                  commercialName: '',
                }))
              }}
              description="Así aparecerá en el contexto autorizado de tu equipo."
              pattern=".*\S.*"
            />
            <div className="firm-field-pair">
              <Field
                label="Correo de contacto"
                name="contactEmail"
                error={fieldErrors.contactEmail ?? ''}
                type="email"
                maxLength={254}
                value={email}
                readOnly={!profile.canManage}
                onChange={(event) => {
                  setEmail(event.target.value)
                  setFieldErrors((previous) => ({
                    ...previous,
                    contactEmail: '',
                  }))
                }}
                autoComplete="email"
              />
              <Field
                label="Teléfono (opcional)"
                name="phone"
                error={fieldErrors.phone ?? ''}
                maxLength={30}
                pattern="[+0-9 \(\)\-]{7,30}"
                value={phone}
                readOnly={!profile.canManage}
                onChange={(event) => {
                  setPhone(event.target.value)
                  setFieldErrors((previous) => ({ ...previous, phone: '' }))
                }}
                autoComplete="tel"
              />
            </div>
            <div className="firm-field-pair">
              <div className="field">
                <label htmlFor="firm-time-zone">Zona horaria</label>
                <select
                  id="firm-time-zone"
                  className="input"
                  value={zone}
                  disabled={!profile.canManage}
                  onChange={(event) =>
                    setZone(event.target.value as Profile['timeZone'])
                  }
                >
                  {firmTimeZones.map((value) => (
                    <option key={value} value={value}>
                      {zoneLabels[value]}
                    </option>
                  ))}
                </select>
              </div>
              <Field
                label="Idioma y región"
                value="Español · México"
                readOnly
                description="Configuración regional de Fiscuvia."
              />
            </div>
          </fieldset>
          {failure && (
            <div className="firm-error" role="alert">
              <p>{failure}</p>
              <Button
                variant="secondary"
                onClick={() =>
                  void client.invalidateQueries({ queryKey: ['firm-settings'] })
                }
              >
                Volver a consultar
              </Button>
            </div>
          )}
          {profile.canManage && (
            <div className="firm-form-actions">
              <p>Los cambios se registran en la auditoría.</p>
              <Button
                type="button"
                variant="secondary"
                disabled={!dirty || pending}
                onClick={cancel}
              >
                Cancelar
              </Button>
              <ConfirmationDialog
                title="¿Guardar los datos del despacho?"
                description="El nombre y los datos de contacto se actualizarán para tu equipo. Esta operación quedará auditada."
                confirmLabel="Confirmar cambios"
                onConfirm={save}
                trigger={
                  <Button
                    type="button"
                    disabled={!dirty || pending}
                    onClick={(event) => {
                      if (!form.current?.reportValidity()) {
                        event.preventDefault()
                        form.current
                          ?.querySelector<HTMLInputElement>(':invalid')
                          ?.focus()
                      }
                    }}
                  >
                    Guardar cambios
                  </Button>
                }
              />
            </div>
          )}
        </form>
      </section>
      <p className="firm-storage-note">
        La carga de logos aún no está disponible.
      </p>
    </div>
  )
}
