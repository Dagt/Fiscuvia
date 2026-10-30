import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ArrowLeft, CheckCircle2, Info } from 'lucide-react'
import {
  EmptyState,
  ErrorState,
  LoadingState,
  PermissionState,
} from '../../shared/ui/States'
import { PageHeader } from '../../shared/ui/PageHeader'
import { useNotification } from '../../shared/presentation/notification-context'

const examples = [
  { id: 'vacio', label: 'Sin elementos' },
  { id: 'carga', label: 'Cargando' },
  { id: 'error', label: 'Error recuperable' },
  { id: 'permisos', label: 'Sin permisos' },
] as const

function StatePreview({ state }: { state: string }) {
  const [recovered, setRecovered] = useState(false)
  const notify = useNotification()
  if (state === 'carga') return <LoadingState />
  if (state === 'permisos') return <PermissionState />
  if (state === 'error')
    return recovered ? (
      <div className="state-view">
        <div className="state-icon">
          <CheckCircle2 aria-hidden="true" />
        </div>
        <h2>Demostración recuperada</h2>
        <p>
          El reintento actualizó esta vista local. No se realizó una solicitud a
          ningún servicio.
        </p>
      </div>
    ) : (
      <ErrorState
        onRetry={() => {
          setRecovered(true)
          notify('El reintento de demostración terminó correctamente.')
        }}
      />
    )
  return <EmptyState />
}

export function StatesPage() {
  const [params] = useSearchParams()
  const selected =
    examples.find(({ id }) => id === params.get('estado'))?.id ?? 'vacio'
  return (
    <div className="page-container">
      <Link className="back-link" to="/desarrollo">
        <ArrowLeft size={16} aria-hidden="true" />
        Volver a la bienvenida
      </Link>
      <PageHeader
        title="Cada estado, una respuesta clara"
        description="Explora cómo la interfaz comunica lo que está pasando y el siguiente paso."
      />
      <div className="demo-notice">
        <Info size={19} aria-hidden="true" />
        <p>
          <strong>Ejemplos de interfaz.</strong> Estos estados son
          demostrativos. No representan datos ni permisos de una cuenta real.
        </p>
      </div>
      <section
        className="surface state-gallery"
        aria-label="Demostraciones de estados"
      >
        <nav
          className="state-options"
          aria-label="Elegir estado de demostración"
        >
          {examples.map(({ id, label }) => (
            <Link
              key={id}
              to={`?estado=${id}`}
              className="state-option"
              aria-current={selected === id ? 'page' : undefined}
            >
              {label}
            </Link>
          ))}
        </nav>
        <StatePreview key={selected} state={selected} />
        <div className="gallery-footer">
          <span>Vista demostrativa</span>
          <span>Componente reutilizable</span>
        </div>
      </section>
      <div className="route-example">
        <div>
          <h2>También hay un camino de regreso</h2>
          <p>
            Una dirección inexistente ofrece una salida clara hacia el inicio.
          </p>
        </div>
        <Link className="text-link" to="/desarrollo/ejemplo-inexistente">
          Ver ejemplo de página inexistente
          <ArrowLeft className="rotate-180" size={16} aria-hidden="true" />
        </Link>
      </div>
    </div>
  )
}
