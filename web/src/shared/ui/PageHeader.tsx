import type { ReactNode } from 'react'

export function PageHeader({
  title,
  description,
  action,
  eyebrow = 'ENTORNO DE DESARROLLO',
  breadcrumbs,
  primaryAction,
  secondaryActions,
}: {
  title: string
  description?: string
  action?: ReactNode
  eyebrow?: string
  breadcrumbs?: ReactNode
  primaryAction?: ReactNode
  secondaryActions?: ReactNode
}) {
  return (
    <div className="page-header">
      <div>
        {breadcrumbs}
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="page-description">{description}</p>
      </div>
      {(action || primaryAction || secondaryActions) && (
        <div className="component-actions">
          {secondaryActions}
          {primaryAction}
          {action}
        </div>
      )}
    </div>
  )
}
