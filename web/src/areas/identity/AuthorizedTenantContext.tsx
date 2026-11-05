import { ArrowRight, Building2 } from 'lucide-react'
import type { Membership } from '../../shared/api/session-client'
import { roleLabel } from './role-labels'
export function AuthorizedTenantContext({
  memberships,
  onChoose,
  pending,
}: {
  memberships: readonly Membership[]
  onChoose: (tenantId: string) => void
  pending: boolean
}) {
  if (!memberships.length)
    return (
      <div className="auth-notice">
        <Building2 size={28} aria-hidden="true" />
        <h2>Aún no tienes despachos asignados</h2>
        <p>
          Tu identidad está verificada. Solicita una invitación a la persona
          administradora de tu despacho.
        </p>
      </div>
    )
  return (
    <div className="auth-memberships">
      {memberships.map((member) => (
        <button
          key={`${member.tenantId}:${member.membershipId}:${member.role}`}
          className="auth-membership"
          disabled={pending || member.tenantStatus === 'SUSPENDED'}
          onClick={() => onChoose(member.tenantId)}
        >
          <Building2 aria-hidden="true" />
          <span>
            <strong>
              {member.commercialName || 'Nombre comercial pendiente'}
            </strong>
            <small>
              {member.tenantStatus === 'SUSPENDED'
                ? 'Suspendido · Sin acceso operativo'
                : roleLabel[member.role]}
            </small>
          </span>
          <ArrowRight size={18} aria-hidden="true" />
        </button>
      ))}
    </div>
  )
}
