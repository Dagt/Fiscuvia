import { useState } from 'react'
import { FirmSettings } from './FirmSettings'
import { PlanUsage } from '../entitlements/PlanUsage'
import { Button } from '../../shared/ui/button'
import { TeamInvitations } from '../team/TeamInvitations'
export function FirmWorkspace(props: {
  userId: string
  tenantId: string
  onUnavailable: (status: number) => void
}) {
  const [area, setArea] = useState<'settings' | 'usage' | 'team'>('settings')
  return (
    <>
      <div className="component-actions" aria-label="Secciones del despacho">
        <Button
          variant={area === 'settings' ? 'primary' : 'secondary'}
          aria-pressed={area === 'settings'}
          onClick={() => setArea('settings')}
        >
          Datos del despacho
        </Button>
        <Button
          variant={area === 'usage' ? 'primary' : 'secondary'}
          aria-pressed={area === 'usage'}
          onClick={() => setArea('usage')}
        >
          Plan y uso
        </Button>
        <Button
          variant={area === 'team' ? 'primary' : 'secondary'}
          aria-pressed={area === 'team'}
          onClick={() => setArea('team')}
        >
          Equipo
        </Button>
      </div>
      {area === 'settings' ? (
        <FirmSettings {...props} />
      ) : area === 'usage' ? (
        <PlanUsage {...props} />
      ) : (
        <TeamInvitations {...props} onUsage={() => setArea('usage')} />
      )}
    </>
  )
}
