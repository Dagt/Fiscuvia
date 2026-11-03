import type { ReactNode } from 'react'
import { Button } from './button'
export type ViewState = 'ready' | 'loading' | 'empty' | 'error' | 'unauthorized'
export function StatCard({
  title,
  value,
  description,
  state = 'ready',
  onRetry,
  action,
}: {
  title: string
  value?: string
  description: string
  state?: ViewState
  onRetry?: () => void
  action?: ReactNode
}) {
  const labels = {
    loading: 'Cargando…',
    empty: 'Sin información',
    error: 'No disponible',
    unauthorized: 'Sin acceso',
  }
  return (
    <section className="stat-card" aria-busy={state === 'loading'}>
      <h3>{title}</h3>
      <p className="stat-value">
        {state === 'ready' ? (value ?? 'Sin información') : labels[state]}
      </p>
      <p className="field-help">{description}</p>
      {state === 'error' && onRetry && (
        <Button variant="ghost" onClick={onRetry}>
          Reintentar
        </Button>
      )}
      {state === 'ready' && action}
    </section>
  )
}
