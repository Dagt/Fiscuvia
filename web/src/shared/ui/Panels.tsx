import { useRef, useState, type ReactNode } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { Button } from './button'

export function SideDrawer({
  title,
  description,
  trigger,
  children,
  footer,
}: {
  title: string
  description: string
  trigger: ReactNode
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <Dialog.Root>
      <Dialog.Trigger asChild>{trigger}</Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="layer-overlay" />
        <Dialog.Content className="side-drawer">
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
          <div className="drawer-body">{children}</div>
          {footer && <footer>{footer}</footer>}
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
  destructive = false,
}: {
  trigger: ReactNode
  title: string
  description: string
  confirmLabel: string
  onConfirm: () => Promise<void> | void
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
          {failed && (
            <p className="field-error" role="alert">
              No pudimos completar la acción. Puedes reintentar.
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
