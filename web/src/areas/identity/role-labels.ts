import type { Membership } from '../../shared/api/session-client'
export const roleLabel: Record<Membership['role'], string> = {
  FIRM_OWNER_ADMIN: 'Administración del despacho',
  FIRM_SUPERVISOR: 'Supervisión',
  ACCOUNTANT: 'Contabilidad',
  ASSISTANT: 'Asistencia',
}
