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
      {memberships.map((member, index) => (
        <button
          key={member.membershipId}
          className="auth-membership"
          disabled={pending}
          onClick={() => onChoose(member.tenantId)}
        >
          <Building2 aria-hidden="true" />
          <span>
            <strong>Despacho {index + 1}</strong>
            <small>{roleLabel[member.role]}</small>
          </span>
          <ArrowRight size={18} aria-hidden="true" />
        </button>
      ))}
    </div>
  )
}
