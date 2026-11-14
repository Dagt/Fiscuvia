import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import {
  ArrowLeft,
  Building2,
  CalendarDays,
  FileCheck2,
  ShieldCheck,
  UserRound,
} from 'lucide-react'
import { taxpayerDetail, type Taxpayer } from '../../shared/api/taxpayer-client'
import { ApiClientError } from '../../shared/api/client'
import { Button } from '../../shared/ui/button'
import { StatusPill } from '../../shared/ui/StatusPill'
import { SideDrawer } from '../../shared/ui/Panels'
import {
  LoadingState,
  ErrorState,
  PermissionState,
} from '../../shared/ui/States'
import { useNotification } from '../../shared/presentation/notification-context'
import { TaxpayerForm } from './TaxpayerForm'
import { ProfileSections } from './ProfileSections'
import { TaxpayerLifecycle } from './TaxpayerLifecycle'
import './taxpayer-profile.css'

type Props = {
  userId: string
  tenantId: string
  membershipId: string
  id: string
  onUnavailable: (status: number) => void
}
type Section = 'summary' | 'information'
const date = (v: string) =>
  new Intl.DateTimeFormat('es-MX', { dateStyle: 'long' }).format(new Date(v))
const person = (t: Taxpayer) =>
  t.personType === 'INDIVIDUAL' ? 'Persona física' : 'Persona moral'
export function TaxpayerProfile(props: Props) {
  return (
    <Profile
      key={`${props.userId}:${props.tenantId}:${props.membershipId}:${props.id}`}
      {...props}
    />
  )
}
function Profile(props: Props) {
  const client = useQueryClient(),
    notify = useNotification(),
    live = useRef(true),
    heading = useRef<HTMLHeadingElement>(null)
  const [section, setSection] = useState<Section>('summary'),
    [editing, setEditing] = useState(false),
    [retained, setRetained] = useState(false),
    [lost, setLost] = useState<number | null>(null)
  const prefix = [
    'taxpayer-profile',
    props.userId,
    props.tenantId,
    props.membershipId,
    props.id,
  ]
  const validId =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      props.id,
    )
  useEffect(() => {
    live.current = true
    return () => {
      live.current = false
      notify(null)
    }
  }, [notify])
  function unavailable(status: number) {
    if (!live.current) return
    setLost(status)
    setEditing(false)
    notify(null)
    void client
      .cancelQueries({ queryKey: prefix })
      .then(() => client.removeQueries({ queryKey: prefix }))
    if (status === 401 || status === 403) props.onUnavailable(status)
  }
  const query = useQuery({
    queryKey: [...prefix, 'base'],
    enabled: validId && lost === null,
    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    queryFn: async ({ signal }) => {
      try {
        return await taxpayerDetail(props.tenantId, props.id, signal)
      } catch (e) {
        if (
          e instanceof ApiClientError &&
          [401, 403, 404].includes(e.status ?? 0)
        )
          unavailable(e.status!)
        throw e
      }
    },
  })
  const back = (
    <Button asChild variant="ghost">
      <Link to="/app/taxpayers">
        <ArrowLeft size={18} aria-hidden="true" />
        Volver a contribuyentes
      </Link>
    </Button>
  )
  function refresh() {
    setEditing(false)
    void client.invalidateQueries({ queryKey: prefix })
    void client.invalidateQueries({
      queryKey: [
        'taxpayer-directory',
        props.userId,
        props.tenantId,
        props.membershipId,
      ],
    })
    void client.invalidateQueries({
      queryKey: ['tenant-usage', props.userId, props.tenantId],
    })
  }
  function saved() {
    refresh()
    notify({
      message: 'Operación confirmada. Expediente actualizado.',
      tone: 'success',
    })
  }
  if (!validId || lost !== null)
    return (
      <section className="taxpayer-profile">
        {back}
        <PermissionState
          title="Expediente no disponible"
          description="No pudimos abrir este expediente con tu acceso actual. Comprueba el despacho seleccionado o vuelve al directorio autorizado."
        />
      </section>
    )
  if (query.isPending || query.isFetching)
    return (
      <section className="taxpayer-profile">
        {back}
        <LoadingState title="Consultando el expediente autorizado" />
      </section>
    )
  if (query.isError)
    return (
      <section className="taxpayer-profile">
        {back}
        <ErrorState onRetry={() => void query.refetch()} />
      </section>
    )
  const { taxpayer: t, actions } = query.data,
    Icon = t.personType === 'INDIVIDUAL' ? UserRound : Building2
  const sections = [
    { id: 'summary', label: 'Resumen', available: true },
    { id: 'information', label: 'Información y contactos', available: true },
    {
      id: 'responsibilities',
      label: 'Responsables y accesos',
      available: false,
    },
    { id: 'certificates', label: 'Certificados y alertas', available: false },
    { id: 'activity', label: 'Actividad e historial', available: false },
  ]
  return (
    <article className="taxpayer-profile" aria-label="Ficha del contribuyente">
      <div className="taxpayer-profile-back">
        {back}
        <span>Expediente administrativo</span>
      </div>
      <header className="taxpayer-profile-hero">
        <div className="taxpayer-profile-identity">
          <div className="taxpayer-profile-mark">
            <Icon size={28} aria-hidden="true" />
          </div>
          <div>
            <p className="eyebrow">CONTRIBUYENTE DEL DESPACHO</p>
            <h1 ref={heading} tabIndex={-1}>
              {t.legalName}
            </h1>
            {t.tradeName && (
              <p className="taxpayer-profile-trade">{t.tradeName}</p>
            )}
            <div className="taxpayer-profile-facts">
              <span className="taxpayer-rfc">{t.rfc}</span>
              <span>{person(t)}</span>
              <StatusPill tone={t.status === 'ACTIVE' ? 'success' : 'neutral'}>
                {t.status === 'ACTIVE' ? 'Activo' : 'Archivado'}
              </StatusPill>
            </div>
          </div>
        </div>
        <div className="taxpayer-profile-actions">
          {actions.canEdit && (
            <Button disabled={retained} onClick={() => setEditing(true)}>
              Editar información
            </Button>
          )}
          <TaxpayerLifecycle
            key={`${t.id}:${t.version}`}
            tenant={props.tenantId}
            taxpayer={t}
            canArchive={actions.canArchive}
            canRestore={actions.canRestore}
            onSaved={saved}
            onUnavailable={unavailable}
            onReload={refresh}
            onRetain={setRetained}
          />
        </div>
      </header>
      <ProfileSections
        items={sections}
        selected={section}
        onChange={(id) => setSection(id as Section)}
      >
        <div className="taxpayer-profile-layout">
          <div className="taxpayer-profile-main">
            {section === 'summary' ? (
              <>
                <section className="taxpayer-profile-panel">
                  <div className="taxpayer-profile-section-title">
                    <FileCheck2 size={21} aria-hidden="true" />
                    <h2>Identificación del expediente</h2>
                  </div>
                  <p className="taxpayer-profile-intro">
                    Datos administrativos registrados por el despacho para este
                    contribuyente.
                  </p>
                  <Identity taxpayer={t} />
                </section>
                <RegimeSection taxpayer={t} />
              </>
            ) : (
              <>
                <section className="taxpayer-profile-panel">
                  <h2>Información general</h2>
                  <Identity taxpayer={t} />
                </section>
                <RegimeSection taxpayer={t} />
                <section className="taxpayer-profile-panel">
                  <h2>Contactos</h2>
                  <p>
                    La consulta y gestión de contactos aún no están habilitadas.
                    No se utilizan como contactos las identidades de los
                    colaboradores.
                  </p>
                </section>
              </>
            )}
          </div>
          <aside
            className="taxpayer-profile-sidebar"
            aria-label="Contexto del expediente"
          >
            <section className="taxpayer-profile-panel">
              <div className="taxpayer-profile-section-title">
                <CalendarDays size={20} aria-hidden="true" />
                <h2>Registro administrativo</h2>
              </div>
              <dl>
                <div>
                  <dt>Incorporación</dt>
                  <dd>
                    <time dateTime={t.createdAt}>{date(t.createdAt)}</time>
                  </dd>
                </div>
                <div>
                  <dt>Última modificación</dt>
                  <dd>
                    <time dateTime={t.updatedAt}>{date(t.updatedAt)}</time>
                  </dd>
                </div>
              </dl>
            </section>
            <div className="taxpayer-profile-assurance">
              <ShieldCheck size={22} aria-hidden="true" />
              <strong>Información registrada, sin verificación SAT</strong>
              <p>
                El RFC pasó validación local de estructura. Las referencias de
                régimen fueron capturadas manualmente y están pendientes de
                revisión.
              </p>
            </div>
          </aside>
        </div>
      </ProfileSections>
      <SideDrawer
        title="Editar información del contribuyente"
        description="Modifica únicamente los datos administrativos permitidos del expediente."
        open={editing}
        onOpenChange={(o) => {
          if (!retained) setEditing(o)
        }}
        className="taxpayer-drawer"
        onCloseAutoFocus={(e) => {
          e.preventDefault()
          if (heading.current) heading.current.focus()
          else document.getElementById('contenido')?.focus()
        }}
      >
        {editing && (
          <TaxpayerForm
            key={t.version}
            tenant={props.tenantId}
            initial={t}
            onSaved={saved}
            onUnavailable={unavailable}
            onReload={refresh}
            onRetain={setRetained}
          />
        )}
      </SideDrawer>
    </article>
  )
}
function Identity({ taxpayer: t }: { taxpayer: Taxpayer }) {
  return (
    <dl className="taxpayer-profile-data">
      <div>
        <dt>Nombre o razón social</dt>
        <dd>{t.legalName}</dd>
      </div>
      <div>
        <dt>RFC</dt>
        <dd className="taxpayer-rfc">{t.rfc}</dd>
      </div>
      <div>
        <dt>Tipo de persona</dt>
        <dd>{person(t)}</dd>
      </div>
      <div>
        <dt>Nombre comercial</dt>
        <dd>{t.tradeName || 'Sin nombre comercial registrado'}</dd>
      </div>
    </dl>
  )
}
function RegimeSection({ taxpayer: t }: { taxpayer: Taxpayer }) {
  return (
    <section className="taxpayer-profile-panel">
      <h2>Referencias manuales de régimen</h2>
      <p className="taxpayer-profile-intro">
        Información capturada manualmente, pendiente de revisión.
      </p>
      {t.regimes.length ? (
        t.regimes.map((r, i) => (
          <div className="taxpayer-profile-regime" key={i}>
            <div>
              <strong>Código {r.code}</strong>
              <p>
                {r.validFrom
                  ? `Desde ${r.validFrom}${r.validTo ? ` · Hasta ${r.validTo} (exclusivo)` : ' · Sin término registrado'}`
                  : 'Vigencia desconocida'}
              </p>
            </div>
            <StatusPill tone="warning">Pendiente de revisión</StatusPill>
          </div>
        ))
      ) : (
        <p className="taxpayer-profile-no-data">
          No hay referencias manuales registradas.
        </p>
      )}
    </section>
  )
}
