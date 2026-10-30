import {
  AlertCircle,
  FileStack,
  LoaderCircle,
  LockKeyhole,
  SearchX,
  RotateCcw,
} from 'lucide-react'
import { Link } from 'react-router'
import { Button } from './button'

export function LoadingState() {
  return (
    <div className="state-view" role="status" aria-live="polite">
      <div className="state-icon">
        <LoaderCircle className="loading-icon" aria-hidden="true" />
      </div>
      <h2>Preparando la vista</h2>
      <p>Un momento, estamos organizando la información.</p>
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
}: {
  title?: string
  description?: string
}) {
  return (
    <div className="state-view">
      <div className="state-icon">
        <FileStack aria-hidden="true" />
      </div>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  )
}

export function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="state-view">
      <div className="state-icon state-icon-error">
        <AlertCircle aria-hidden="true" />
      </div>
      <h2>No pudimos cargar esta vista</h2>
      <p>
        Puedes volver a intentarlo. Si el problema continúa, regresa más tarde.
      </p>
      <Button variant="outline" onClick={onRetry}>
        <RotateCcw size={16} aria-hidden="true" />
        Reintentar
      </Button>
    </div>
  )
}

export function PermissionState() {
  return (
    <div className="state-view">
      <div className="state-icon">
        <LockKeyhole aria-hidden="true" />
      </div>
      <h2>Necesitas permisos para continuar</h2>
      <p>
        Solicita a la persona administradora de tu despacho que revise tu
        acceso.
      </p>
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
