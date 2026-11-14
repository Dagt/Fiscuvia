import { useState } from 'react'
import type { Taxpayer } from '../../shared/api/taxpayer-client'
import { Button } from '../../shared/ui/button'
import { ConfirmationDialog } from '../../shared/ui/Panels'
import { commandMessage, useTaxpayerCommand } from './useTaxpayerCommand'
export function TaxpayerLifecycle({
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
