import { useRef, useState, type ReactNode } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { Button } from './button'
import { EmptyState, ErrorState, LoadingState, PermissionState } from './States'
import type { ViewState } from './StatCard'

export function SideDrawer({
  title,
  description,
  trigger,
  children,
  footer,
  state = 'ready',
  onRetry,
  open,
  onOpenChange,
  onCloseAutoFocus,
  className = '',
}: {
  className?: string
  title: string
  description: string
  trigger?: ReactNode
  children: ReactNode
  footer?: ReactNode
  state?: ViewState
  onRetry?: () => void
  open?: boolean | undefined
  onOpenChange?: ((open: boolean) => void) | undefined
  onCloseAutoFocus?: ((event: Event) => void) | undefined
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      {trigger && <Dialog.Trigger asChild>{trigger}</Dialog.Trigger>}
      <Dialog.Portal>
        <Dialog.Overlay className="layer-overlay" />
        <Dialog.Content
          className={`side-drawer ${className}`}
          aria-busy={state === 'loading'}
          onCloseAutoFocus={onCloseAutoFocus}
        >
          <header className="panel-heading">
            <div>
              <Dialog.Title>{title}</Dialog.Title>
              <Dialog.Description>{description}</Dialog.Description>
            </div>
            <Dialog.Close asChild>
              <Button variant="ghost" iconOnly aria-label="Cerrar panel">
                <X size={20} />
              </Button>
            </Dialog.Close>
          </header>
          <div className="drawer-body">
            {state === 'loading' ? (
              <LoadingState />
            ) : state === 'error' ? (
              onRetry ? (
                <ErrorState onRetry={onRetry} />
              ) : (
                <p role="alert">
                  No pudimos cargar este detalle. Cierra el panel y vuelve a
                  intentar.
                </p>
              )
            ) : state === 'unauthorized' ? (
              <PermissionState />
            ) : state === 'empty' ? (
              <EmptyState />
            ) : (
              children
            )}
          </div>
          {state === 'ready' && footer && <footer>{footer}</footer>}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

export function DetailsPanel({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  return (
    <aside className="details-panel" aria-label={title}>
      <h3>{title}</h3>
      {children}
    </aside>
  )
}

export function ConfirmationDialog({
  trigger,
  title,
  description,
  confirmLabel,
  onConfirm,
  failureMessage = 'No pudimos completar la acción. Puedes reintentar.',
  destructive = false,
  children,
}: {
  children?: ReactNode
  trigger: ReactNode
  title: string
  description: string
  confirmLabel: string
  onConfirm: () => Promise<void> | void
  /** Safe caller-owned copy; never display a raw server/exception message. */
  failureMessage?: string | undefined
  destructive?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [pending, setPending] = useState(false)
  const [failed, setFailed] = useState(false)
  const inFlight = useRef(false)
  const cancel = useRef<HTMLButtonElement>(null)
  async function confirm() {
    if (inFlight.current) return
    inFlight.current = true
    setPending(true)
    setFailed(false)
    try {
      await onConfirm()
      setOpen(false)
    } catch {
      setFailed(true)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(value) => {
        if (!inFlight.current) {
          setOpen(value)
          setFailed(false)
        }
      }}
    >
      <Dialog.Trigger asChild>{trigger}</Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="layer-overlay" />
        <Dialog.Content
          role="alertdialog"
          className="confirmation-dialog"
          onOpenAutoFocus={(event) => {
            event.preventDefault()
            cancel.current?.focus()
          }}
          onEscapeKeyDown={(event) => {
            if (pending) event.preventDefault()
          }}
          onPointerDownOutside={(event) => event.preventDefault()}
        >
          <Dialog.Title>{title}</Dialog.Title>
          <Dialog.Description>{description}</Dialog.Description>
          {children}
          {failed && (
            <p className="field-error" role="alert">
              {failureMessage}
            </p>
          )}
          <div className="component-actions">
            <Dialog.Close asChild>
              <Button ref={cancel} variant="secondary" disabled={pending}>
                Cancelar
              </Button>
            </Dialog.Close>
            <Button
              variant={destructive ? 'danger' : 'primary'}
              pending={pending}
              onClick={() => void confirm()}
            >
              {pending ? 'Procesando…' : confirmLabel}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
