import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  tenantUsage,
  type EntitlementUsage,
} from '../../shared/api/entitlement-client'
import { ApiClientError } from '../../shared/api/client'
import {
  LoadingState,
  ErrorState,
  PermissionState,
} from '../../shared/ui/States'
import { StatCard } from '../../shared/ui/StatCard'
import { StatusPill } from '../../shared/ui/StatusPill'
import './plan-usage.css'

const labels = {
  USERS: 'Usuarios',
  TAXPAYERS: 'Contribuyentes',
  STORAGE_BYTES: 'Almacenamiento',
  JOBS_RUNNING: 'Trabajos simultáneos',
  JOBS_PENDING: 'Trabajos pendientes',
  JOBS_PER_PERIOD: 'Trabajos por período',
}
const number = (value: number | null) =>
  value === null
    ? 'No disponible'
    : new Intl.NumberFormat('es-MX').format(value)
export function PlanUsage({
  userId,
  tenantId,
  onUnavailable,
}: {
  userId: string
  tenantId: string
  onUnavailable: (status: number) => void
}) {
  const client = useQueryClient()
  const key = ['tenant-usage', userId, tenantId]
  const query = useQuery({
    queryKey: key,
    queryFn: async ({ signal }) => {
      try {
        return await tenantUsage(tenantId, signal)
      } catch (error) {
        if (
          error instanceof ApiClientError &&
          (error.status === 401 || error.status === 403)
        ) {
          client.setQueryData(key, [])
          if (error.status === 401) onUnavailable(401)
        }
        throw error
      }
    },
    retry: false,
    staleTime: 0,
  })
  if (query.isPending || query.isFetching)
    return <LoadingState title="Consultando límites y uso" />
  if (query.isError)
    return query.error instanceof ApiClientError &&
      query.error.status === 403 ? (
      <PermissionState
        title="Sin permiso para consultar el plan"
        description="Sólo la persona propietaria del despacho puede consultar esta información. Se requiere una verificación confiable con doble factor de los últimos 10 minutos."
      />
    ) : (
      <ErrorState onRetry={() => void query.refetch()} />
    )
  return (
    <div className="plan-usage">
      <p>
        Los límites técnicos se administran internamente. Esta sección no ofrece
        contratación ni pagos.
      </p>
      {query.data.map((item) => (
        <ResourceUsage key={item.resource} item={item} />
      ))}
    </div>
  )
}
function ResourceUsage({ item }: { item: EntitlementUsage }) {
  const integrated = item.integration === 'MEASURED'
  const unconfigured = item.limit === null
  const expired = integrated && !unconfigured && item.available === null
  const status = unconfigured
    ? 'Sin configuración'
    : expired
      ? 'Configuración no vigente'
      : !integrated
        ? 'Recurso aún no integrado'
        : item.overLimit
          ? 'Sobrecupo'
          : item.available === 0
            ? 'Límite alcanzado'
            : 'Capacidad disponible'
  return (
    <section className="firm-card" aria-label={labels[item.resource]}>
      <div className="firm-section-heading">
        <h2>{labels[item.resource]}</h2>
        <StatusPill
          tone={
            item.overLimit
              ? 'danger'
              : item.available === 0
                ? 'warning'
                : 'neutral'
          }
        >
          {status}
        </StatusPill>
      </div>
      <p>
        {item.resource === 'USERS'
          ? 'Se cuentan membresías activas del despacho. Una identidad desactivada conserva su membresía y su cupo.'
          : item.resource === 'STORAGE_BYTES'
            ? 'Unidad: bytes. La medición se habilitará con el almacenamiento privado.'
            : item.resource === 'JOBS_PER_PERIOD'
              ? 'El período de medición está pendiente de definición.'
              : 'Unidad: cantidad. La medición se habilitará al integrar el módulo.'}
      </p>
      <div className="usage-stats">
        <StatCard
          title="Límite configurado"
          value={unconfigured ? 'Sin configurar' : number(item.limit)}
          description={
            item.version === null
              ? 'No significa capacidad ilimitada.'
              : `Versión ${item.version}`
          }
        />
        <StatCard
          title="Uso medido"
          value={integrated ? number(item.measured) : 'Sin consumo medido'}
          description={
            integrated
              ? 'Membresías activas existentes.'
              : 'No existe una fuente de consumo integrada.'
          }
        />
        <StatCard
          title="Uso reservado"
          value={integrated ? number(item.reserved) : 'No integrado'}
          description="Capacidad pendiente de confirmar o liberar."
        />
        <StatCard
          title="Capacidad disponible"
          value={number(item.available)}
          description={
            item.overLimit
              ? 'Se conservan los datos existentes. No hay capacidad para nuevas altas.'
              : 'Sujeta a validación del servidor en cada operación.'
          }
        />
      </div>
      {item.available === 0 && integrated && (
        <p role="status">
          No hay cupo disponible. Contacta al equipo de Fiscuvia para revisar
          los límites del despacho.
        </p>
      )}
    </section>
  )
}
