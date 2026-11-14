import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Plus,
  ArrowUpRight,
  ShieldCheck,
  UsersRound,
  RefreshCw,
} from 'lucide-react'
import {
  listTaxpayers,
  taxpayerPresentation,
  taxpayerDetail,
  type Taxpayer,
  type TaxpayerQuery,
  type Receipt,
} from '../../shared/api/taxpayer-client'
import { ApiClientError } from '../../shared/api/client'
import { PageHeader } from '../../shared/ui/PageHeader'
import { Button } from '../../shared/ui/button'
import { DataTable, type Column } from '../../shared/ui/DataTable'
import { FilterBar } from '../../shared/ui/FilterBar'
import { StatusPill } from '../../shared/ui/StatusPill'
import { ConfirmationDialog, SideDrawer } from '../../shared/ui/Panels'
import {
  EmptyState,
  LoadingState,
  PermissionState,
  ErrorState,
} from '../../shared/ui/States'
import { useNotification } from '../../shared/presentation/notification-context'
import { TaxpayerForm } from './TaxpayerForm'
import { commandMessage, useTaxpayerCommand } from './useTaxpayerCommand'
import './taxpayers.css'

type Props = {
  userId: string
  tenantId: string
  membershipId: string
  onUnavailable: (s: number) => void
}
const initialQuery: TaxpayerQuery = {
  page: 0,
  size: 20,
  search: '',
  status: 'ACTIVE',
  sort: 'legalName,asc',
  regime: '',
}
const formatDate = (v: string) =>
  new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(v))
const person = (v: string) =>
  v === 'INDIVIDUAL' ? 'Persona física' : 'Persona moral'
const status = (v: string) => (
  <StatusPill tone={v === 'ACTIVE' ? 'success' : 'neutral'}>
    {v === 'ACTIVE' ? 'Activo' : 'Archivado'}
  </StatusPill>
)
export function TaxpayerDirectory(props: Props) {
  return (
    <Directory
      key={`${props.userId}:${props.tenantId}:${props.membershipId}`}
      {...props}
    />
  )
}
function Directory(props: Props) {
  const client = useQueryClient(),
    notify = useNotification(),
    [query, setQuery] = useState<TaxpayerQuery>(initialQuery),
    [search, setSearch] = useState(''),
    [regime, setRegime] = useState(''),
    [blocked, setBlocked] = useState(false)
  const [retained, setRetained] = useState(false)
  const [view, setView] = useState<
    { kind: 'create' } | { kind: 'detail'; id: string } | null
  >(null)
  const title = useRef<HTMLDivElement>(null),
    live = useRef(true),
    returnFocus = useRef<HTMLElement | null>(null)
  const prefix = [
    'taxpayer-directory',
    props.userId,
    props.tenantId,
    props.membershipId,
  ]
  const settled = search === query.search && regime === query.regime
  useEffect(() => {
    const timer = setTimeout(
      () => setQuery((q) => ({ ...q, search, regime, page: 0 })),
      300,
    )
    return () => clearTimeout(timer)
  }, [search, regime])
  useEffect(() => {
    if (!settled)
      void client.cancelQueries({
        queryKey: [
          'taxpayer-directory',
          props.userId,
          props.tenantId,
          props.membershipId,
          'list',
        ],
      })
  }, [settled, client, props.userId, props.tenantId, props.membershipId])
  useEffect(() => {
    live.current = true
    return () => {
      live.current = false
      notify(null)
    }
  }, [notify])
  function unavailable(code: number) {
    if (!live.current) return
    setView(null)
    notify(null)
    void client
      .cancelQueries({ queryKey: prefix })
      .then(() => client.removeQueries({ queryKey: prefix }))
    if (code === 401) {
      setBlocked(true)
      props.onUnavailable(401)
    } else if (code === 403) {
      setBlocked(true)
    } else {
      notify({
        message:
          'El expediente o la operación ya no están disponibles con tu acceso actual.',
        tone: 'warning',
      })
      refresh()
    }
  }
  const hints = useQuery({
    queryKey: [...prefix, 'presentation'],
    enabled: !blocked,
    retry: false,
    gcTime: 0,
    staleTime: 0,
    refetchOnWindowFocus: true,
    queryFn: async ({ signal }) => {
      try {
        return await taxpayerPresentation(props.tenantId, undefined, signal)
      } catch (e) {
        if (
          e instanceof ApiClientError &&
          (e.status === 401 || e.status === 403)
        )
          unavailable(e.status)
        throw e
      }
    },
  })
  const list = useQuery({
    queryKey: [...prefix, 'list', query],
    enabled: !blocked && settled && (!regime || /^[0-9]{3}$/.test(regime)),
    retry: false,
    gcTime: 0,
    staleTime: 0,
    refetchOnWindowFocus: true,
    queryFn: async ({ signal }) => {
      try {
        const data = await listTaxpayers(props.tenantId, query, signal)
        if (
          !signal.aborted &&
          live.current &&
          data.items.length === 0 &&
          query.page > 0
        )
          setQuery((q) => ({ ...q, page: Math.max(0, q.page - 1) }))
        return data
      } catch (e) {
        if (
          e instanceof ApiClientError &&
          (e.status === 401 || e.status === 403)
        )
          unavailable(e.status)
        throw e
      }
    },
  })
  function refresh() {
    void client.invalidateQueries({ queryKey: prefix })
    void client.invalidateQueries({
      queryKey: ['tenant-usage', props.userId, props.tenantId],
    })
  }
  function open(next: NonNullable<typeof view>, element: HTMLElement) {
    returnFocus.current = element
    setView(next)
  }
  function saved(receipt: Receipt) {
    refresh()
    setView({ kind: 'detail', id: receipt.targetId })
    notify({
      message: receipt.replayed
        ? 'Resultado recuperado y confirmado.'
        : 'Contribuyente registrado correctamente.',
      tone: 'success',
    })
  }
  const filtered = !!query.search || !!query.regime || query.status !== 'ACTIVE'
  const columns: Column<Taxpayer>[] = [
    {
      id: 'legalName',
      header: 'Nombre o razón social',
      sortable: true,
      cell: (t) => (
        <div className="taxpayer-name">
          <strong>{t.legalName}</strong>
          {t.tradeName && <span>{t.tradeName}</span>}
        </div>
      ),
    },
    {
      id: 'rfc',
      header: 'RFC / Persona',
      cell: (t) => (
        <div className="taxpayer-identity">
          <span className="taxpayer-rfc">{t.rfc}</span>
          <span>{person(t.personType)}</span>
        </div>
      ),
    },
    {
      id: 'regimes',
      header: 'Régimen · Manual',
      cell: (t) => <Regimes taxpayer={t} />,
    },
    { id: 'status', header: 'Estado', cell: (t) => status(t.status) },
    {
      id: 'updatedAt',
      header: 'Última modificación',
      cell: (t) => (
        <time dateTime={t.updatedAt}>{formatDate(t.updatedAt)}</time>
      ),
    },
    {
      id: 'actions',
      header: 'Acciones',
      cell: (t) => (
        <Button
          variant="ghost"
          iconOnly
          title="Ver información"
          aria-label={`Ver información de ${t.legalName}`}
          onClick={(e) => open({ kind: 'detail', id: t.id }, e.currentTarget)}
        >
          <ArrowUpRight size={17} aria-hidden="true" />
        </Button>
      ),
    },
  ]
  return (
    <section
      className="taxpayer-area"
      aria-label="Directorio de contribuyentes"
    >
      <div ref={title} tabIndex={-1}>
        <PageHeader
          eyebrow="GESTIÓN DEL DESPACHO"
          title="Contribuyentes"
          description="Administra los contribuyentes y su información fiscal registrada en el despacho."
          primaryAction={
            !blocked &&
            !hints.isFetching &&
            !hints.isError &&
            hints.data?.canCreate ? (
              <Button
                onClick={(e) => open({ kind: 'create' }, e.currentTarget)}
              >
                <Plus size={18} aria-hidden="true" />
                Nuevo contribuyente
              </Button>
            ) : undefined
          }
        />
      </div>
      {blocked ? (
        <PermissionState
          title="Sin acceso al directorio de contribuyentes"
          description="Tu acceso fiscal no está disponible. Solicita revisar la membresía y los permisos efectivos del despacho."
          action={
            <Button
              variant="secondary"
              onClick={() => props.onUnavailable(403)}
            >
              Revisar acceso al despacho
            </Button>
          }
        />
      ) : (
        <>
          {hints.data?.createBlock === 'MFA_REQUIRED' && (
            <div className="taxpayer-command-notice" role="status">
              Para registrar contribuyentes necesitas una verificación MFA
              reciente. Vuelve a autenticarte para realizar acciones
              administrativas.
            </div>
          )}
          {hints.data?.createBlock === 'QUOTA_UNAVAILABLE' && (
            <div className="taxpayer-command-notice" role="status">
              No hay cupo disponible o el límite no está vigente. Solicita
              revisar el plan del despacho para registrar contribuyentes.
            </div>
          )}
          {hints.isError && (
            <div className="taxpayer-command-notice" role="alert">
              No pudimos verificar las acciones disponibles.
              <Button variant="ghost" onClick={() => void hints.refetch()}>
                Reintentar permisos
              </Button>
            </div>
          )}
          <div className="taxpayer-directory-surface">
            <FilterBar
              search={search}
              searchLabel="Buscar por RFC o nombre"
              searchMaxLength={80}
              onSearchChange={(v) => setSearch(v)}
              filters={[
                {
                  id: 'status',
                  label: 'Estado',
                  value: query.status,
                  options: [
                    { value: 'ACTIVE', label: 'Activos' },
                    { value: 'ARCHIVED', label: 'Archivados' },
                  ],
                  onChange: (v) =>
                    setQuery((q) => ({
                      ...q,
                      status: v as TaxpayerQuery['status'],
                      page: 0,
                    })),
                },
              ]}
              activeCount={
                Number(!!search) +
                Number(!!regime) +
                Number(query.status !== 'ACTIVE')
              }
              onClear={() => {
                setSearch('')
                setRegime('')
                setQuery({
                  ...initialQuery,
                  size: query.size,
                  sort: query.sort,
                })
              }}
              extra={
                <div className="taxpayer-secondary-filters">
                  <div className="field">
                    <label htmlFor="taxpayer-regime-filter">
                      Código de régimen (manual)
                    </label>
                    <input
                      id="taxpayer-regime-filter"
                      className="input"
                      inputMode="numeric"
                      maxLength={3}
                      placeholder="Tres dígitos"
                      value={regime}
                      aria-describedby={
                        regime && !/^[0-9]{3}$/.test(regime)
                          ? 'taxpayer-regime-filter-error'
                          : undefined
                      }
                      aria-invalid={
                        (!!regime && !/^[0-9]{3}$/.test(regime)) || undefined
                      }
                      onChange={(e) => setRegime(e.target.value)}
                    />
                  </div>
                  <div className="field">
                    <label htmlFor="taxpayer-sort">Ordenar por</label>
                    <select
                      id="taxpayer-sort"
                      className="input"
                      value={query.sort}
                      onChange={(e) =>
                        setQuery((q) => ({
                          ...q,
                          sort: e.target.value as TaxpayerQuery['sort'],
                          page: 0,
                        }))
                      }
                    >
                      <option value="legalName,asc">Nombre · A–Z</option>
                      <option value="legalName,desc">Nombre · Z–A</option>
                      <option value="createdAt,desc">
                        Incorporación · recientes
                      </option>
                      <option value="createdAt,asc">
                        Incorporación · anteriores
                      </option>
                      <option value="id,asc">Identificador · ascendente</option>
                      <option value="id,desc">
                        Identificador · descendente
                      </option>
                    </select>
                  </div>
                  <div className="field">
                    <label htmlFor="taxpayer-size">Por página</label>
                    <select
                      id="taxpayer-size"
                      className="input"
                      value={query.size}
                      onChange={(e) =>
                        setQuery((q) => ({
                          ...q,
                          size: Number(e.target.value),
                          page: 0,
                        }))
                      }
                    >
                      {[10, 20, 50, 100].map((n) => (
                        <option value={n} key={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  </div>
                  <Button
                    variant="ghost"
                    aria-label="Actualizar directorio"
                    onClick={refresh}
                  >
                    <RefreshCw size={18} aria-hidden="true" />
                  </Button>
                </div>
              }
            />
            {!!regime && !/^[0-9]{3}$/.test(regime) ? (
              <p
                id="taxpayer-regime-filter-error"
                className="field-error"
                role="alert"
              >
                Introduce los tres dígitos del régimen capturado.
              </p>
            ) : !settled || list.isFetching || list.isPending ? (
              <LoadingState title="Consultando contribuyentes autorizados" />
            ) : list.isError ? (
              <ErrorState onRetry={() => void list.refetch()} />
            ) : !list.data?.items.length ? (
              <EmptyState
                title={
                  filtered
                    ? 'No encontramos coincidencias'
                    : 'Tu cartera empieza aquí'
                }
                description={
                  filtered
                    ? 'Prueba otro RFC, nombre o filtro. Sólo se muestran los expedientes que tienes autorizados.'
                    : 'Aún no hay contribuyentes activos visibles con tu acceso. Los registros que puedas consultar aparecerán aquí.'
                }
                action={
                  filtered ? (
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setSearch('')
                        setRegime('')
                        setQuery(initialQuery)
                      }}
                    >
                      Limpiar búsqueda y filtros
                    </Button>
                  ) : undefined
                }
              />
            ) : (
              <DataTable
                label="Contribuyentes autorizados"
                rows={list.data.items}
                columns={columns}
                rowKey={(t) => t.id}
                rowLabel={(t) => t.legalName}
                page={query.page + 1}
                pageSize={query.size}
                hasNext={list.data.hasNext}
                onPageChange={(p) => setQuery((q) => ({ ...q, page: p - 1 }))}
                compactBelow={1023}
                sort={{
                  column: query.sort.split(',')[0]!,
                  direction: query.sort.endsWith('asc') ? 'asc' : 'desc',
                }}
                onSortChange={(s) =>
                  setQuery((q) => ({
                    ...q,
                    sort: `${s.column},${s.direction}` as TaxpayerQuery['sort'],
                    page: 0,
                  }))
                }
                onRetry={() => void list.refetch()}
                renderMobile={(t) => (
                  <>
                    <div className="taxpayer-mobile-identity">
                      <span className="taxpayer-rfc">{t.rfc}</span>
                      {status(t.status)}
                    </div>
                    <dl>
                      <div>
                        <dt>Tipo de persona</dt>
                        <dd>{person(t.personType)}</dd>
                      </div>
                      <div>
                        <dt>Referencias manuales</dt>
                        <dd>
                          <Regimes taxpayer={t} />
                        </dd>
                      </div>
                      <div>
                        <dt>Última modificación</dt>
                        <dd>{formatDate(t.updatedAt)}</dd>
                      </div>
                    </dl>
                    <Button
                      variant="secondary"
                      onClick={(e) =>
                        open({ kind: 'detail', id: t.id }, e.currentTarget)
                      }
                    >
                      Ver información
                      <ArrowUpRight size={17} aria-hidden="true" />
                    </Button>
                  </>
                )}
              />
            )}
          </div>
          <p className="taxpayer-scope-note">
            <ShieldCheck size={17} aria-hidden="true" />
            Cartera autorizada de este despacho. Referencias manuales, sin
            verificación oficial ante el SAT.
          </p>
        </>
      )}
      <SideDrawer
        open={view !== null && !blocked}
        onOpenChange={(o) => {
          if (!o && !retained) setView(null)
        }}
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          if (returnFocus.current?.isConnected) returnFocus.current.focus()
          else title.current?.focus()
        }}
        title={
          view?.kind === 'create'
            ? 'Nuevo contribuyente'
            : 'Información administrativa'
        }
        description={
          view?.kind === 'create'
            ? 'Registra su identificación y las referencias fiscales que conoces.'
            : 'Datos registrados en el despacho, sujetos a tu acceso vigente.'
        }
        className="taxpayer-drawer"
      >
        {view?.kind === 'create' ? (
          <TaxpayerForm
            tenant={props.tenantId}
            onSaved={saved}
            onRetain={setRetained}
            onUnavailable={unavailable}
          />
        ) : view?.kind === 'detail' ? (
          <TaxpayerDetails
            key={view.id}
            tenant={props.tenantId}
            id={view.id}
            prefix={prefix}
            refresh={refresh}
            onRetain={setRetained}
            onUnavailable={unavailable}
          />
        ) : null}
      </SideDrawer>
    </section>
  )
}
function Regimes({ taxpayer }: { taxpayer: Taxpayer }) {
  return (
    <span className="taxpayer-regime-summary">
      {taxpayer.regimes.length
        ? taxpayer.regimes
            .map((r) => r.code)
            .filter((v, i, a) => a.indexOf(v) === i)
            .join(' · ')
        : 'Sin referencias'}
      {taxpayer.regimes.length > 0 && <small>Pendiente de revisión</small>}
    </span>
  )
}
function TaxpayerDetails({
  tenant,
  id,
  prefix,
  refresh,
  onUnavailable,
  onRetain,
}: {
  onRetain: (retain: boolean) => void
  tenant: string
  id: string
  prefix: string[]
  refresh: () => void
  onUnavailable: (s: number) => void
}) {
  const [retained, setRetained] = useState(false)
  const retain = useCallback(
    (value: boolean) => {
      setRetained(value)
      onRetain(value)
    },
    [onRetain],
  )
  const [editing, setEditing] = useState(false),
    client = useQueryClient(),
    notify = useNotification()
  const query = useQuery({
    queryKey: [...prefix, 'detail', id],
    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    queryFn: async ({ signal }) => {
      try {
        return await taxpayerDetail(tenant, id, signal)
      } catch (e) {
        if (
          e instanceof ApiClientError &&
          [401, 403, 404].includes(e.status ?? 0)
        )
          onUnavailable(e.status!)
        throw e
      }
    },
  })
  function reload() {
    setEditing(false)
    void query.refetch()
  }
  if (query.isFetching || query.isPending)
    return <LoadingState title="Consultando información autorizada" />
  if (query.isError) return <ErrorState onRetry={reload} />
  const { taxpayer: t, actions } = query.data
  function confirmed() {
    setEditing(false)
    refresh()
    void client.invalidateQueries({ queryKey: [...prefix, 'detail', id] })
    notify({
      message: 'Operación confirmada. Información actualizada.',
      tone: 'success',
    })
  }
  return editing ? (
    <>
      <Button
        variant="ghost"
        disabled={retained}
        onClick={() => setEditing(false)}
      >
        Volver a la información
      </Button>
      <TaxpayerForm
        key={t.version}
        tenant={tenant}
        initial={t}
        onSaved={confirmed}
        onRetain={retain}
        onUnavailable={onUnavailable}
        onReload={reload}
      />
    </>
  ) : (
    <>
      <div className="taxpayer-detail-heading">
        <div className="taxpayer-avatar">
          <UsersRound aria-hidden="true" />
        </div>
        <h2>{t.legalName}</h2>
        <p className="taxpayer-rfc">{t.rfc}</p>
        {status(t.status)}
      </div>
      <dl className="taxpayer-detail-grid">
        <div>
          <dt>Tipo de persona</dt>
          <dd>{person(t.personType)}</dd>
        </div>
        <div>
          <dt>Nombre comercial</dt>
          <dd>{t.tradeName || 'Sin nombre comercial registrado'}</dd>
        </div>
        <div>
          <dt>Incorporación</dt>
          <dd>{formatDate(t.createdAt)}</dd>
        </div>
        <div>
          <dt>Última modificación</dt>
          <dd>{formatDate(t.updatedAt)}</dd>
        </div>
      </dl>
      <section className="taxpayer-detail-regimes">
        <h3>Referencias manuales de régimen</h3>
        <p className="field-help">
          Información capturada manualmente, pendiente de revisión.
        </p>
        {!t.regimes.length ? (
          <p>Sin referencias registradas.</p>
        ) : (
          t.regimes.map((r, i) => (
            <div className="taxpayer-reference" key={i}>
              <strong>Código {r.code}</strong>
              <span>
                {r.validFrom
                  ? `Desde ${r.validFrom}${r.validTo ? ` hasta ${r.validTo} (exclusivo)` : ' · Sin término registrado'}`
                  : 'Vigencia desconocida'}
              </span>
              <StatusPill tone="warning">Pendiente de revisión</StatusPill>
            </div>
          ))
        )}
      </section>
      <p className="taxpayer-disclaimer">
        <ShieldCheck size={18} aria-hidden="true" />
        Formato y estructura local revisados. Sin verificación oficial ante el
        SAT.
      </p>
      <div className="taxpayer-detail-actions">
        {actions.canEdit && (
          <Button onClick={() => setEditing(true)}>Editar información</Button>
        )}
        <TaxpayerLifecycle
          key={`${t.id}:${t.version}:${t.status}`}
          tenant={tenant}
          taxpayer={t}
          canArchive={actions.canArchive}
          canRestore={actions.canRestore}
          onSaved={confirmed}
          onRetain={retain}
          onUnavailable={onUnavailable}
          onReload={reload}
        />
      </div>
      <p className="field-help">
        Las acciones dependen de tus permisos efectivos y de una verificación
        MFA vigente cuando corresponda.
      </p>
    </>
  )
}
function TaxpayerLifecycle({
  tenant,
  taxpayer: t,
  canArchive,
  canRestore,
  onSaved,
  onUnavailable,
  onReload,
  onRetain,
}: {
  onRetain: (retain: boolean) => void
  tenant: string
  taxpayer: Taxpayer
  canArchive: boolean
  canRestore: boolean
  onSaved: () => void
  onUnavailable: (s: number) => void
  onReload: () => void
}) {
  const [reason, setReason] = useState(''),
    c = useTaxpayerCommand(tenant, onSaved, onUnavailable, onRetain),
    archive = t.status === 'ACTIVE',
    allowed = archive ? canArchive : canRestore
  if (!allowed) return null
  const options = archive
    ? [
        ['SERVICE_ENDED', 'Servicio terminado'],
        ['DUPLICATE_REVIEW', 'Revisión de duplicado'],
        ['OTHER', 'Otro motivo'],
      ]
    : [
        ['SERVICE_RESUMED', 'Servicio reanudado'],
        ['CORRECTION', 'Corrección'],
      ]
  return (
    <div className="taxpayer-lifecycle">
      {c.error && (
        <p className="field-error" role="alert">
          {commandMessage(c.error)}
        </p>
      )}
      {c.error?.code === 'STALE_VERSION' && (
        <Button variant="secondary" onClick={onReload}>
          Actualizar datos para revisar
        </Button>
      )}
      {c.uncertain ? (
        <Button pending={c.busy} onClick={() => void c.run()}>
          Recuperar resultado
        </Button>
      ) : (
        <ConfirmationDialog
          trigger={
            <Button variant={archive ? 'danger' : 'secondary'}>
              {archive ? 'Archivar' : 'Restaurar'} contribuyente
            </Button>
          }
          title={archive ? 'Archivar contribuyente' : 'Restaurar contribuyente'}
          description={
            archive
              ? 'Se conserva el historial. El RFC seguirá reservado dentro del despacho; archivar no elimina el expediente.'
              : 'Se conservará el mismo expediente. Restaurar requiere capacidad disponible y autorización vigente.'
          }
          confirmLabel={
            archive ? 'Confirmar archivado' : 'Confirmar restauración'
          }
          destructive={archive}
          failureMessage={
            commandMessage(c.error) || 'No se confirmó la operación.'
          }
          onConfirm={async () => {
            const reasonCode = reason || options[0]![0]!
            const ok = await c.run(
              archive
                ? {
                    action: 'archive',
                    id: t.id,
                    body: {
                      expectedVersion: t.version,
                      reasonCode: reasonCode as
                        'SERVICE_ENDED' | 'DUPLICATE_REVIEW' | 'OTHER',
                    },
                  }
                : {
                    action: 'restore',
                    id: t.id,
                    body: {
                      expectedVersion: t.version,
                      reasonCode: reasonCode as
                        'SERVICE_RESUMED' | 'CORRECTION',
                    },
                  },
            )
            if (!ok) throw new Error('Unconfirmed')
          }}
        >
          <div className="field">
            <label htmlFor="taxpayer-reason">Motivo</label>
            <select
              className="input"
              id="taxpayer-reason"
              value={reason || options[0]![0]}
              onChange={(e) => setReason(e.target.value)}
              disabled={c.busy}
            >
              {options.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </select>
          </div>
        </ConfirmationDialog>
      )}
    </div>
  )
}
