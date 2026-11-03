import {
  AlertCircle,
  FileStack,
  LoaderCircle,
  LockKeyhole,
  SearchX,
  RotateCcw,
} from 'lucide-react'
import { Link } from 'react-router'
import type { ReactNode } from 'react'
type StateCopy = { title?: string; description?: string; action?: ReactNode }
import { Button } from './button'

export function LoadingState({
  title = 'Preparando la vista',
  description = 'Un momento, estamos organizando la información.',
}: StateCopy = {}) {
  return (
    <div className="state-view" role="status" aria-live="polite">
      <div className="state-icon">
        <LoaderCircle className="loading-icon" aria-hidden="true" />
      </div>
      <h2>{title}</h2>
      <p>{description}</p>
      <div className="skeleton-lines" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
    </div>
  )
}

export function EmptyState({
  title = 'Un espacio para empezar',
  description = 'Todavía no hay elementos que mostrar. Cuando estén disponibles, aparecerán aquí.',
  action,
}: {
  title?: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="state-view">
      <div className="state-icon">
        <FileStack aria-hidden="true" />
      </div>
      <h2>{title}</h2>
      <p>{description}</p>
      {action}
    </div>
  )
}

export function ErrorState({
  onRetry,
  title = 'No pudimos cargar esta vista',
  description = 'Puedes volver a intentarlo. Si el problema continúa, regresa más tarde.',
}: StateCopy & { onRetry: () => void }) {
  return (
    <div className="state-view">
      <div className="state-icon state-icon-error">
        <AlertCircle aria-hidden="true" />
      </div>
      <h2>{title}</h2>
      <p>{description}</p>
      <Button variant="outline" onClick={onRetry}>
        <RotateCcw size={16} aria-hidden="true" />
        Reintentar
      </Button>
    </div>
  )
}

export function PermissionState({
  title = 'Necesitas permisos para continuar',
  description = 'Solicita a la persona administradora de tu despacho que revise tu acceso.',
  action,
}: StateCopy = {}) {
  return (
    <div className="state-view">
      <div className="state-icon">
        <LockKeyhole aria-hidden="true" />
      </div>
      <h2>{title}</h2>
      <p>{description}</p>
      {action}
    </div>
  )
}

export function NotFoundState() {
  return (
    <div className="state-view">
      <div className="state-icon">
        <SearchX aria-hidden="true" />
      </div>
      <h1>No encontramos esta página</h1>
      <p>La dirección no existe o la sección todavía no está disponible.</p>
      <Button asChild>
        <Link to="/desarrollo">Volver al inicio</Link>
      </Button>
    </div>
  )
}
