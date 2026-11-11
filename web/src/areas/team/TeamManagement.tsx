import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Users } from 'lucide-react'
import {
  teamMembers,
  teamInvitations,
  mutateTeam,
  type TeamMember,
  type TeamInvitation,
  type TeamRole,
  type TeamMutation,
} from '../../shared/api/team-admin-client'
import { ApiClientError } from '../../shared/api/client'
import { Button } from '../../shared/ui/button'
import { Field } from '../../shared/ui/Field'
import { DataTable } from '../../shared/ui/DataTable'
import { StatusPill } from '../../shared/ui/StatusPill'
import { ConfirmationDialog, SideDrawer } from '../../shared/ui/Panels'
import {
  LoadingState,
  PermissionState,
  ErrorState,
} from '../../shared/ui/States'
import { useNotification } from '../../shared/presentation/notification-context'
import { TeamInvitations } from './TeamInvitations'
import './team.css'

type Props = {
  userId: string
  tenantId: string
  onUnavailable: (status: number) => void
  onUsage: () => void
}
const roleLabels = {
  FIRM_OWNER_ADMIN: 'Propietario',
  FIRM_SUPERVISOR: 'Supervisor',
  ACCOUNTANT: 'Contador',
  ASSISTANT: 'Auxiliar',
}
const statusLabels = {
  ACTIVE: 'Activa',
  SUSPENDED: 'Suspendida',
  REVOKED: 'Revocada',
  PENDING: 'Pendiente',
  ACCEPTED: 'Aceptada',
  EXPIRED: 'Vencida',
}
const labelRole = (role: string) =>
  roleLabels[role as keyof typeof roleLabels] ?? 'No disponible'
const name = (m: { displayName: string | null; email: string | null }) =>
  m.displayName || m.email || 'Identidad sin nombre disponible'
const date = (v: string) =>
  new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(v))
const pill = (status: keyof typeof statusLabels) => (
  <StatusPill
    tone={
      status === 'ACTIVE' || status === 'ACCEPTED'
        ? 'success'
        : status === 'PENDING' || status === 'SUSPENDED'
          ? 'warning'
          : 'neutral'
    }
  >
    {statusLabels[status]}
  </StatusPill>
)
export function TeamManagement(props: Props) {
  return <TeamPanel key={`${props.userId}:${props.tenantId}`} {...props} />
}
function TeamPanel(props: Props) {
  const [view, setView] = useState<'members' | 'invitations' | 'invite'>(
    'members',
  )
  const [page, setPage] = useState(0),
    [search, setSearch] = useState(''),
    [status, setStatus] = useState('ALL')
  const [blocked, setBlocked] = useState(false)
  const [selected, setSelected] = useState<TeamMember | null>(null)
  const returnFocus = useRef<HTMLButtonElement>(null)
  const client = useQueryClient()
  const prefix = ['team-directory', props.userId, props.tenantId]
  function unavailable(code: number) {
    setBlocked(true)
    void client.cancelQueries({ queryKey: prefix })
    client.removeQueries({ queryKey: prefix })
    if (code === 401) props.onUnavailable(401)
  }
  const query = useQuery({
    queryKey: [...prefix, view, page, search, status],
    enabled: !blocked && view !== 'invite',
    retry: false,
    staleTime: 0,
    gcTime: 0,
    queryFn: async ({ signal }) => {
      try {
        return view === 'members'
          ? await teamMembers(
              props.tenantId,
              { page, size: 20, search, status },
              signal,
            )
          : await teamInvitations(
              props.tenantId,
              { page, size: 20, search, status },
              signal,
            )
      } catch (error) {
        if (
          error instanceof ApiClientError &&
          (error.status === 401 || error.status === 403)
        )
          unavailable(error.status)
        throw error
      }
    },
  })
  function select(next: typeof view) {
    setSelected(null)
    setView(next)
    setPage(0)
    setSearch('')
    setStatus('ALL')
  }
  function refresh() {
    void client.invalidateQueries({ queryKey: prefix })
    void client.invalidateQueries({
      queryKey: ['tenant-usage', props.userId, props.tenantId],
    })
    // Team commands cannot change the actor. Refetching its presentation context here
    // would unmount the workspace and discard the active team view unnecessarily.
  }
  if (blocked)
    return (
      <PermissionState
        title="Sin permiso para administrar el equipo"
        description="Se requiere ser propietario del despacho activo y contar con doble factor confiable de los últimos 10 minutos. Vuelve a autenticarte si tu verificación venció."
      />
    )
  if (view !== 'invite' && query.isPending)
    return <LoadingState title="Consultando equipo autorizado" />
  const pending = query.isFetching || query.isPending
  const common = {
    page: page + 1,
    pageSize: 20,
    onPageChange: (n: number) => setPage(n - 1),
    onRetry: () => void query.refetch(),
  }
  const callbacks = {
    tenant: props.tenantId,
    onSaved: refresh,
    onUnavailable: unavailable,
  }
  return (
    <section className="team-area" aria-label="Administración del equipo">
      <div className="firm-brand">
        <span className="firm-monogram">
          <Users aria-hidden="true" size={26} />
        </span>
        <div>
          <h2>Tu equipo, en una sola vía</h2>
          <p>Colaboradores e invitaciones de tu despacho autorizado.</p>
        </div>
      </div>
      <div className="component-actions" aria-label="Vistas del equipo">
        <Button
          ref={returnFocus}
          aria-pressed={view === 'members'}
          variant={view === 'members' ? 'primary' : 'secondary'}
          onClick={() => select('members')}
        >
          Colaboradores
        </Button>
        <Button
          aria-pressed={view === 'invitations'}
          variant={view === 'invitations' ? 'primary' : 'secondary'}
          onClick={() => select('invitations')}
        >
          Invitaciones
        </Button>
        <Button
          aria-pressed={view === 'invite'}
          variant={view === 'invite' ? 'primary' : 'secondary'}
          onClick={() => select('invite')}
        >
          Nueva invitación
        </Button>
        <Button variant="ghost" onClick={props.onUsage}>
          Consultar cupo
        </Button>
      </div>
      {view === 'invite' ? (
        <TeamInvitations {...props} />
      ) : (
        <>
          <div className="team-filters">
            <Field
              label="Buscar por nombre o correo"
              type="search"
              value={search}
              maxLength={80}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              autoComplete="off"
            />
            <label className="team-role">
              <span>Estado</span>
              <select
                className="input"
                aria-label="Estado"
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value)
                  setPage(0)
                }}
              >
                <option value="ALL">Todos</option>
                {(view === 'members'
                  ? ['ACTIVE', 'SUSPENDED', 'REVOKED']
                  : ['PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED']
                ).map((s) => (
                  <option key={s} value={s}>
                    {statusLabels[s as keyof typeof statusLabels]}
                  </option>
                ))}
              </select>
            </label>
            <Button
              variant="secondary"
              onClick={() => {
                setSearch('')
                setStatus('ALL')
                setPage(0)
              }}
            >
              Limpiar filtros
            </Button>
            <Button variant="secondary" onClick={() => void query.refetch()}>
              Actualizar lista
            </Button>
          </div>
          {pending ? (
            <LoadingState title="Consultando equipo autorizado" />
          ) : query.isError ? (
            <ErrorState onRetry={() => void query.refetch()} />
          ) : view === 'members' ? (
            <DataTable<TeamMember>
              {...common}
              label="Colaboradores del despacho"
              rows={(query.data?.items ?? []) as TeamMember[]}
              total={query.data?.total ?? 0}
              rowKey={(m) => `${m.membershipId}:${m.version}`}
              rowLabel={name}
              columns={[
                {
                  id: 'name',
                  header: 'Colaborador',
                  cell: (m) => (
                    <div className="team-person">
                      <strong>{name(m)}</strong>
                      <span>{m.email || 'Correo no disponible'}</span>
                      {m.identityStatus === 'DISABLED' && (
                        <StatusPill tone="warning">
                          Identidad deshabilitada
                        </StatusPill>
                      )}
                    </div>
                  ),
                },
                {
                  id: 'role',
                  header: 'Rol',
                  cell: (m) => m.roles.map(labelRole).join(', '),
                },
                {
                  id: 'status',
                  header: 'Membresía',
                  cell: (m) => pill(m.status),
                },
                {
                  id: 'joined',
                  header: 'Incorporación',
                  cell: (m) => date(m.joinedAt),
                },
                {
                  id: 'actions',
                  header: 'Administración',
                  cell: (m) => (
                    <MemberActions member={m} onEdit={() => setSelected(m)} />
                  ),
                },
              ]}
              renderMobile={(m) => (
                <>
                  <p>{m.email || 'Correo no disponible'}</p>
                  <p>{m.roles.map(labelRole).join(', ')}</p>
                  {pill(m.status)}
                  {m.identityStatus === 'DISABLED' && (
                    <p>Identidad deshabilitada</p>
                  )}
                  <p>Incorporación: {date(m.joinedAt)}</p>
                  <MemberActions member={m} onEdit={() => setSelected(m)} />
                </>
              )}
            />
          ) : (
            <DataTable<TeamInvitation>
              {...common}
              label="Invitaciones del despacho"
              rows={(query.data?.items ?? []) as TeamInvitation[]}
              total={query.data?.total ?? 0}
              rowKey={(i) => `${i.invitationId}:${i.version}`}
              rowLabel={name}
              columns={[
                {
                  id: 'recipient',
                  header: 'Destinatario',
                  cell: (i) => (
                    <div className="team-person">
                      <strong>{name(i)}</strong>
                      <span>{i.email || 'Correo no disponible'}</span>
                    </div>
                  ),
                },
                { id: 'role', header: 'Rol', cell: (i) => labelRole(i.role) },
                { id: 'status', header: 'Estado', cell: (i) => pill(i.status) },
                {
                  id: 'expiry',
                  header: 'Vencimiento',
                  cell: (i) => date(i.expiresAt),
                },
                {
                  id: 'actions',
                  header: 'Administración',
                  cell: (i) =>
                    i.status === 'PENDING' ? (
                      <CommandControl
                        action={{
                          kind: 'revoke',
                          target: i.invitationId,
                          expectedVersion: i.version,
                        }}
                        label="Revocar invitación"
                        description="La invitación pendiente dejará de poder aceptarse. No elimina ninguna membresía."
                        {...callbacks}
                      />
                    ) : (
                      <span>Sin acciones disponibles</span>
                    ),
                },
              ]}
            />
          )}
          {view === 'invitations' && (
            <p className="field-description">
              El historial no contiene tokens. La creación local requiere
              entrega manual; no se envían correos externos.
            </p>
          )}
        </>
      )}
      {selected && (
        <SideDrawer
          title="Administrar colaborador"
          description={`${name(selected)} · ${selected.email || 'Correo no disponible'}`}
          open
          onCloseAutoFocus={(event) => {
            event.preventDefault()
            returnFocus.current?.focus()
          }}
          onOpenChange={(open) => {
            if (!open) setSelected(null)
          }}
        >
          <MemberEditor
            key={`${selected.membershipId}:${selected.version}`}
            member={selected}
            {...callbacks}
            onSaved={() => {
              setSelected(null)
              refresh()
            }}
          />
        </SideDrawer>
      )}
      <aside className="team-notice">
        <p>
          La asignación de cartera estará disponible después de aprobar el
          modelo fiscal. Los roles operativos no conceden permisos fiscales
          automáticamente.
        </p>
      </aside>
    </section>
  )
}
type Callbacks = {
  tenant: string
  onSaved: () => void
  onUnavailable: (code: number) => void
}
function MemberActions({
  member: m,
  onEdit,
}: {
  member: TeamMember
  onEdit: () => void
}) {
  if (!m.canManage)
    return (
      <span className="field-description">
        {m.roles.includes('FIRM_OWNER_ADMIN')
          ? 'Propietario protegido'
          : 'Sin cambios habilitados'}
      </span>
    )
  if (m.status === 'REVOKED')
    return <span className="field-description">Acceso revocado</span>
  return (
    <Button variant="secondary" onClick={onEdit}>
      Administrar
    </Button>
  )
}

function MemberEditor({
  member: m,
  ...callbacks
}: Callbacks & { member: TeamMember }) {
  const [role, setRole] = useState<TeamRole>(
    (m.roles[0] as TeamRole) || 'ACCOUNTANT',
  )
  return (
    <div className="team-member-actions" aria-label={`Acciones de ${name(m)}`}>
      <label className="team-role">
        <span>Nuevo rol</span>
        <select
          className="input"
          value={role}
          onChange={(e) => setRole(e.target.value as TeamRole)}
          aria-label={`Nuevo rol de ${m.email || name(m)}`}
        >
          <option value="FIRM_SUPERVISOR">Supervisor</option>
          <option value="ACCOUNTANT">Contador</option>
          <option value="ASSISTANT">Auxiliar</option>
        </select>
      </label>
      <CommandControl
        action={{
          kind: 'role',
          target: m.membershipId,
          expectedVersion: m.version,
          role,
        }}
        disabled={m.roles.length !== 1 || role === m.roles[0]}
        label="Cambiar rol"
        description={`Cambiar de ${m.roles.map(labelRole).join(', ')} a ${labelRole(role)}. La membresía y el doble factor se validan nuevamente en el servidor.`}
        {...callbacks}
      />
      <CommandControl
        action={{
          kind: 'status',
          target: m.membershipId,
          expectedVersion: m.version,
          status: m.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE',
        }}
        disabled={m.status === 'SUSPENDED' && m.identityStatus !== 'ACTIVE'}
        label={m.status === 'ACTIVE' ? 'Suspender' : 'Reactivar'}
        description={
          m.status === 'ACTIVE'
            ? 'Se retirará el acceso operativo al despacho. Se conservará el historial.'
            : 'Se verificará el cupo antes de activar. Una identidad deshabilitada no puede reactivarse desde aquí.'
        }
        {...callbacks}
      />
      <CommandControl
        action={{
          kind: 'status',
          target: m.membershipId,
          expectedVersion: m.version,
          status: 'REVOKED',
        }}
        label="Revocar acceso"
        description="Se retirará el acceso de forma definitiva para esta membresía. No podrá reactivarse desde esta pantalla y se conservará la autoría histórica."
        {...callbacks}
      />
    </div>
  )
}
function CommandControl({
  action,
  label,
  description,
  disabled = false,
  tenant,
  onSaved,
  onUnavailable,
}: Callbacks & {
  action: TeamMutation
  label: string
  description: string
  disabled?: boolean
}) {
  const notify = useNotification(),
    alive = useRef(true),
    transport = useRef<AbortController | null>(null)
  const key = useRef<{ fingerprint: string; key: string } | null>(null)
  const [pending, setPending] = useState(false),
    [failure, setFailure] = useState<string | null>(null)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      transport.current?.abort()
    }
  }, [])
  async function confirm() {
    if (pending) return
    setPending(true)
    setFailure(null)
    const fingerprint = JSON.stringify(action)
    if (key.current?.fingerprint !== fingerprint)
      key.current = { fingerprint, key: crypto.randomUUID() }
    transport.current = new AbortController()
    try {
      const result = await mutateTeam(
        tenant,
        action,
        key.current.key,
        transport.current.signal,
      )
      if (!alive.current) return
      notify(
        result.replayed
          ? 'La solicitud ya estaba confirmada. Consultando el estado actual.'
          : 'Cambio confirmado por el servidor.',
      )
      onSaved()
    } catch (error) {
      if (!alive.current) return
      if (
        error instanceof ApiClientError &&
        (error.status === 401 || error.status === 403)
      )
        onUnavailable(error.status)
      const message =
        error instanceof ApiClientError && error.code === 'QUOTA_UNAVAILABLE'
          ? 'No hay cupo disponible. Consulta Plan y uso antes de reactivar.'
          : error instanceof ApiClientError &&
              (error.status === 409 || error.status === 404)
            ? 'La información cambió o la operación ya no está disponible. Actualiza la lista antes de continuar.'
            : 'No pudimos confirmar el cambio. Puedes reintentar la misma solicitud de forma segura.'
      setFailure(message)
      throw new Error(message, { cause: error })
    } finally {
      if (alive.current) setPending(false)
    }
  }
  return (
    <div className="team-command">
      <ConfirmationDialog
        title={label}
        description={description}
        confirmLabel={label}
        failureMessage={failure ?? undefined}
        destructive={
          action.kind === 'revoke' ||
          (action.kind === 'status' && action.status !== 'ACTIVE')
        }
        onConfirm={confirm}
        trigger={
          <Button
            variant={label.startsWith('Revocar') ? 'danger' : 'secondary'}
            disabled={disabled}
            pending={pending}
          >
            {label}
          </Button>
        }
      />
      {failure && (
        <p role="alert" className="field-error">
          {failure}
        </p>
      )}
    </div>
  )
}
